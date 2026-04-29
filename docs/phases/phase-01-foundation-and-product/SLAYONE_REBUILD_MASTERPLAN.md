# Slay.one Rebuild Masterplan

## 硬参考

1. `reference-assets/pics/ScreenShot_2026-04-19_133846_325.png`
   - 约束主菜单的按钮数量
   - 约束中央菜单窗的地位
   - 约束“像游戏，不像网站”的母版

2. `reference-assets/pics/微信图片_20260418183447_107_1.jpg`
   - 约束 battle 内左上 / 右下分工
   - 约束 preview -> `查看全部` 的层级
   - 约束文字页必须退到二级

## 首页母版

1. 全屏视频背景
2. 中央单一主菜单窗
3. 主动作：开始
4. 次动作：配装
5. 一个很小的额外入口：回放
6. 左上：回放 / 论坛 / 排行
7. 右下：邮件 / 好友

## `/battle` 母版

1. 中央永远是战斗
2. 顶部无多余状态小框
3. 左上：回放 / 论坛 / 排行
4. 右下：邮件 / 好友
5. active battle 时隐藏附属入口
6. 战后只保留：
   - 再来一局
   - 查看回放
   - 查看变化
   - 返回大厅

## 文本页定位

这些页面只作为 `查看全部` 之后的正文页存在：

- `/replay`
- `/replay/:id`
- `/mails`
- `/rating`
- `/contribution`
- `/profile/:handle`
- `/discussion`
- `/discussion/:id`
