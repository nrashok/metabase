(ns metabase.query-processor-test.query-cancellation-test
  (:require [clojure.java.jdbc :as jdbc]
            [expectations :refer [expect]]
            [metabase
             [query-processor-test :refer :all]
             [util :as u]]
            [metabase.driver.generic-sql.query-processor :as gqp]
            [metabase.query-processor.middleware.expand :as ql]
            [metabase.test.data :as data]
            [metabase.test.data
             [dataset-definitions :as defs]
             [datasets :as datasets]]
            [metabase.test.util :as tu]))

(deftype FakePreparedStatement [called-cancel?]
  java.sql.PreparedStatement
  (cancel [_] (deliver called-cancel? true))
  (close [_] true))

(defn- make-fake-prep-stmt
  "Returns `fake-value` whenenver the `sql` parameter returns a truthy value when passed to `use-fake-value?`"
  [orig-make-prep-stmt use-fake-value? faked-value]
  (fn [connection sql options]
    (if (use-fake-value? sql)
      faked-value
      (orig-make-prep-stmt connection sql options))))

(defn- fake-query
  "Function to replace the `clojure.java.jdbc/query` function. Will invoke `call-on-query`, then `call-to-pause` whe
  passed an instance of `FakePreparedStatement`"
  [orig-jdbc-query call-on-query call-to-pause]
  (fn
    ([conn stmt+params]
     (orig-jdbc-query conn stmt+params))
    ([conn stmt+params opts]
     (if (instance? FakePreparedStatement (first stmt+params))
       (do
         (call-on-query)
         (call-to-pause))
       (orig-jdbc-query conn stmt+params opts)))))

(expect
  [false false true false true true]
  (data/with-db (data/get-or-create-database! defs/test-data)
    ;; There is work being done in several threads below, the promises are used to coordinate actions between those threads
    (let [called-cancel?             (promise)
          called-query?              (promise)
          pause-query                (promise)
          ;; This fake prepared statement is cancellable like a prepared statement, but will allow us to tell the
          ;; difference between our Prepared statement and the real thing
          fake-prep-stmt             (->FakePreparedStatement called-cancel?)
          ;; Much of the underlying plumbing of MB requires a working jdbc/query and jdbc/prepared-statement (such as
          ;; queryies for the application database). Let binding the original versions of the functions allows us to
          ;; delegate to them when it's not the query we're trying to test
          orig-jdbc-query            jdbc/query
          orig-prep-stmt             jdbc/prepare-statement
          before-query-called-cancel (realized? called-cancel?)
          before-query-called-query  (realized? called-query?)
          ;; When the query is ran via the datasets endpoint, it will run in a future. That future can be cancelled,
          ;; which should cause an interrupt
          query-future               (future
                                       (with-redefs [jdbc/prepare-statement (make-fake-prep-stmt orig-prep-stmt #(re-find #"VENUES" %) fake-prep-stmt)
                                                     jdbc/query             (fake-query orig-jdbc-query #(deliver called-query? true) #(deref pause-query))]
                                         (data/run-query venues
                                           (ql/aggregation (ql/count)))))]

      ;; Make sure that we start out with our promises not having a value
      [before-query-called-cancel
       before-query-called-query
       ;; The query should be called, set a 2 minute limit in case things go very wrong
       (deref called-query? 120000 ::query-never-called)
       ;; At this point in time, the query is blocked, waiting for `pause-query` do be delivered
       (realized? called-cancel?)
       (do
         ;; If we cancel the future, it should throw an InterruptedException, which should call the cancel method on
         ;; the prepared statement
         (future-cancel query-future)
         (deref called-cancel? 120000 ::cancel-never-called))
       (do
         ;; This releases the fake clojure.java.jdbc/query function so it finishes
         (deliver pause-query true)
         true)])))
