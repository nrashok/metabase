import React, { Component } from "react";
import PropTypes from "prop-types";
import cx from "classnames";

export default class LogoIcon extends Component {
    static defaultProps = {
        size: 32
    };

    static propTypes = {
        size: PropTypes.number,
        width: PropTypes.number,
        height: PropTypes.number,
        dark: PropTypes.bool
    };

    render() {
        let { dark, height, width, size } = this.props;
        return (
            <svg className={cx('Icon', {"text-brand": !dark }, { "text-white": dark })} viewBox="0 0 66 85" width={width || size} height={height || size} fill="currentcolor">
			<path d="M14 0 H18 L19 6 L20.707 6.707 L26 3.293 L28.707 6 L25.293 11.293 L26 13 L32 14 V18 L26 19 L25.293 20.707 L28.707 26 L26 28.707 L20.707 25.293 L19 26 L18 32 L14 32 L13 26 L11.293 25.293 L6 28.707 L3.293 26 L6.707 20.707 L6 19 L0 18 L0 14 L6 13 L6.707 11.293 L3.293 6 L6 3.293 L11.293 6.707 L13 6 L14 0 z M16 10 A6 6 0 0 0 16 22 A6 6 0 0 0 16 10"></path>
            </svg>
        );
    }
}
