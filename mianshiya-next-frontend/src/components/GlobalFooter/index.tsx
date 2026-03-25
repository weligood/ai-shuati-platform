import React from "react";
import "./index.css";

/**
 * 全局底部栏组件
 * @constructor
 */
export default function GlobalFooter() {
  const currentYear = new Date().getFullYear();

  return (
    <div className="global-footer">
      <div>© {currentYear} 面试刷题平台</div>
      <div>
        <a href="https://github.com/weligood" target="_blank">
          请关注作者仓库:https://github.com/weligood
        </a>
      </div>
    </div>
  );
}

