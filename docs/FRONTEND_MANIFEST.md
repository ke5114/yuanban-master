# Frontend Manifest

更新时间: 2026-05-10

本清单覆盖两个前端：uni-app 小程序端和 Vue 管理端。小程序页面来源于 `frontend/mini-program/pages.json`；管理端页面来源于 `frontend/admin/src/router/index.js`，接口封装来源于 `frontend/admin/src/utils/admin-api.js`。

## 摘要

- 小程序页面数量: 46
- 管理端路由页面数量: 11
- 小程序主包: `frontend/mini-program/pages/**`
- 小程序分包: `frontend/mini-program/subpkg/**`
- 管理端路由基座: `/admin/`

## 小程序端页面

| 页面路径 | 标题 | 核心交互逻辑 | 主要接口/动作 |
| --- | --- | --- | --- |
| `pages/auth/login` | 登录 | 用户/陪诊师登录入口，支持账号密码和微信登录分流；微信小程序走 `uni.login` code 登录，微信内 H5 走公众号网页授权回调，未绑定时进入手机号绑定；App 微信登录代码已预留，需后续整包启用原生微信 OAuth 模块。 | `/api/users/login`<br>`/api/users/wechat/config-status`<br>`/api/users/wechat/login`<br>`/api/users/wechat/oauth-url` |
| `pages/role-user/order` | 我的订单 | 用户订单列表，查看订单状态、进入详情、处理待支付/服务中/评价等流程；分类 Tab 支持待退款状态，对待支付、待确认时长、待补差额和全部显示待处理红点；列表项返回 `attendantId` 后，点击陪诊师头像可直接进入陪诊师主页查看资质。 | `/api/orders/{...}`<br>`/api/orders/user-orders` |
| `pages/role-user/message` | 消息中心 | 用户消息中心，加载聊天联系人和系统消息，监听系统消息已读并同步底部红点。 | `/api/chat/contacts` |
| `pages/role-user/profile` | 个人中心 | 用户个人中心，展示账户信息，头像展示优先使用后端 `avatar` 并同步 `avatar/avatarUrl` 缓存，进入资料编辑、订单、消息、设置等入口；退出登录使用自定义底部确认面板。 | 无直接接口调用/通过封装模块调用 |
| `pages/ai-triage/01-appointment-selection` | 预约类型选择 | 预约类型选择，承接首页/AI 分流，进入普通预约或 AI 导诊。 | 无直接接口调用/通过封装模块调用 |
| `pages/role-escort/hall` | 陪诊接单大厅 | 陪诊师接单大厅，先检查资质门禁；资质待补充、未通过、过期或账号不可用时不请求待接订单并显示阻断态；待审核只显示审核中状态，不弹窗；未通过/过期/待补充只在进入接单厅时用自定义弹窗提示且同一前台会话同状态只提示一次；确认接单弹窗展示患者、服务项目、医院、服务时间和预计收入，取消/确认按钮等宽等高。 | `/attendant/profile/{...}`<br>`/attendant/orders/{...}`<br>`/attendant/orders/{...}/accept?attendantId={...}`<br>`/attendant/orders/{...}/reject-assigned`<br>`/attendant/orders/waiting` |
| `pages/role-escort/order` | 陪诊师订单 | 陪诊师订单列表，按状态查看历史和服务订单；不再因资质未通过自动弹窗，历史订单正常可见；进入页面会刷新专属派单，列表不展示无效倒计时；专属接单时段结束后改显示“专属派单已流转”，不再停留在“待确认”语义。 | `/attendant/profile/{...}`<br>`/attendant/orders`<br>`/attendant/orders/{...}/accept?attendantId={...}`<br>`/attendant/orders/{...}/reject-assigned` |
| `pages/role-escort/message` | 消息中心 | 陪诊师消息中心，加载联系人和系统消息；不因资质未通过自动弹窗；监听系统消息已读并同步自定义底栏红点。 | `/attendant/profile/{...}`<br>`/api/chat/contacts` |
| `pages/role-escort/profile` | 我的 | 陪诊师个人中心，展示资料、收入、资质门禁状态、服务统计入口和按真实评价数展示的好评率；无评价显示“暂无评价”；不因资质未通过自动弹窗，资质入口用状态标签提示；退出登录使用自定义底部确认面板。 | `/attendant/profile/{...}` |
| `pages/role-user/home` | 陪诊服务 | 用户首页，推荐陪诊师、服务入口、AI 助手/AI 导诊入口和陪诊师详情跳转；评分使用 `order_evaluation.rating` 聚合结果，无评价显示“暂无评分”。 | 无直接接口调用/通过封装模块调用 |
| `pages/public/index` | 愈安陪诊 | 公开落地页/角色入口，按登录状态和角色引导进入用户端或陪诊师端；陪诊师人物卡无评价显示“暂无评分”。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/chat/chat` | 聊天 | 用户侧聊天页，加载历史消息、发送文本/图片/语音并同步已读；聊天头部不展示静态在线状态和说明型副标题，只保留对象名称与身份标签。 | `/api/chat/history?targetUserId={...}&page=1&pageSize={...}`<br>`/api/chat/history?targetUserId={...}&page={...}&pageSize={...}`<br>`/api/chat/read?senderId={...}`<br>`/api/chat/send`<br>`/api/common/upload` |
| `subpkg/chat/chat-escort` | 聊天 | 陪诊师侧聊天页，加载历史消息、发送文本/图片/语音并同步已读；支持长按任意消息进入“定向回复”并在发送后携带被回复消息引用，气泡内显示回复摘要，修复只能连续对话无法单独回复的问题；聊天头部不展示静态在线状态和说明型副标题，只保留对象名称与身份标签。 | `/api/chat/history?targetUserId={...}&page=1&pageSize={...}`<br>`/api/chat/history?targetUserId={...}&page={...}&pageSize={...}`<br>`/api/chat/read?senderId={...}`<br>`/api/chat/send`<br>`/api/common/upload` |
| `subpkg/system-message/system-message` | 系统消息 | 系统消息列表，进入后批量标记系统通知已读并同步用户/陪诊师底部消息红点，按消息动作跳转订单详情/评价/接单处理。 | `/ai/guide/orders/{...}/complete-info`<br>`/api/chat/system/{...}`<br>`/attendant/orders/{...}`<br>`/api/orders/{...}`<br>`/api/chat/system`<br>`/api/chat/read?senderId=0` |
| `subpkg/system-message/escort-detail` | 系统消息详情 | 陪诊师系统消息详情，查看派单/订单相关消息并跳转订单；专属派单消息打开已流转订单时保留“曾收到过派单”的上下文。 | `/attendant/orders/{...}`<br>`/api/chat/system/{...}` |
| `subpkg/order/order-detail` | 订单详情 | 用户订单详情，查看订单、支付尾款、取消、申诉、确认时长费用、补差额支付、查看二维码/联系陪诊师；待确认时长时展示陪诊师提交说明，确认按钮采用自适应布局避免文字裁切；时长确认卡中的“预计时长”固定展示下单预估值/预约时段推导值，不被陪诊师提交的 `actualDuration` 覆盖；确认负差额后展示待平台退款，不再直接显示已完成；补差额支付遇到登录态过期时只提示重新登录，不直接跳走当前订单页；陪诊师星级展示平均评分，按 `attendantScore + attendantEvaluationCount` 判断，无评价显示“暂无评分”。 | `/api/orders/{...}/cancel?reason={...}`<br>`/api/orders/{...}/dispute-time-fee{...}`<br>`/api/orders/{...}/confirm-time-fee`<br>`/api/orders/{...}/pay-balance`<br>`/api/orders/{...}/evaluation`<br>`/ai/guide/orders/{...}/complete-info` |
| `subpkg/order/escort-detail` | 订单详情（陪诊师端） | 陪诊师订单详情，专属派单可查看完整资料、确认接单或拒绝派单；专属接单时段结束后展示“曾收到过派单、已转入公共大厅”的流转态和刷新/返回操作；接单后执行开始服务、结束服务、取消、扫码核销、评价查看等；扫码内容不匹配使用自定义提示面板。 | `/attendant/orders/{...}/evaluation`<br>`/attendant/orders/{...}/evaluation/reply`<br>`/attendant/orders/{...}/service-progress?step={...}`<br>`/attendant/orders/{...}`<br>`/attendant/orders/{...}/accept?attendantId={...}`<br>`/attendant/orders/{...}/reject-assigned`<br>`/attendant/orders/{...}/scan-qr?qrCodeContent={...}`<br>`/attendant/orders/{...}/cancel` |
| `subpkg/order/prepare` | 服务前准备清单 | 服务前准备清单，展示陪诊服务前注意事项。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/order/submit-time-fee` | 提交时长与费用 | 陪诊师提交实际服务时长、费用和说明，驱动用户确认或争议流程；说明输入框按卡片宽度对齐，启用自动换行与长词断行并限制容器边界，避免文本超出外层布局；加减时长按钮清理原生按钮边框并居中，避免符号不对称。 | `/attendant/orders/{...}`<br>`/attendant/orders/{...}/end?actualDuration={...}&attendantTimeRemark={...}` |
| `subpkg/evaluate/evaluate` | 订单评价 | 用户订单评价，加载订单和既有评价，提交星级、标签和文字内容。 | `/ai/guide/orders/{...}/complete-info`<br>`/api/orders/{...}/evaluation` |
| `subpkg/auth/escort-register` | 陪诊师入驻 | 陪诊师入驻注册，填写基础信息、擅长领域、医院和简介后提交；注册成功后使用自定义底部引导面板进入登录，带 `after=qualification` 标记，陪诊师登录后进入陪诊师端“我的”，点击接单厅时由资质门禁弹窗提示上传资质。 | `/api/users/register` |
| `subpkg/auth/user-register` | 用户注册 | 用户注册，填写手机号、姓名、密码等基础信息后提交；注册成功后使用自定义底部引导面板进入登录。 | `/api/users/register` |
| `subpkg/auth/wechat-bind` | 绑定手机号 | 微信登录后绑定手机号/姓名/密码，完成账号合并并进入角色首页。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/appointment-flow/01-appointment-selection` | 预约类型选择 | 普通预约流程入口，选择服务类型并进入预约表单。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/appointment-flow/02-appointment-form` | 服务预约 | 普通预约表单，填写患者/医院/时间/需求，创建预约并生成公共待接单订单；普通下单不自动指定陪诊师，支付后进入接单大厅。联系信息输入以 `v-model` 为主绑定并保留统一清洗，兼容 H5/App/小程序；服务时段弹窗滚动区固定在按钮上方，跨日结束时间不挤出按钮；缺失必填项用页面内错误卡提示。 | `/ai/guide/appointments`<br>`/ai/guide/orders`<br>`/api/users/current` |
| `subpkg/appointment-flow/04-order-confirm-page` | 确认订单 | 订单确认与模拟支付，加载完整订单信息并提交支付状态。 | `/ai/guide/orders/{...}/complete-info`<br>`/ai/guide/payments/status` |
| `subpkg/appointment-flow/05-payment-success-page` | 支付成功 | 支付成功页，展示订单摘要并引导查看订单详情。 | `/ai/guide/orders/{...}/complete-info` |
| `subpkg/appointment-flow/06-payment-failed-page` | 支付失败 | 支付失败页，提示重试或返回订单确认。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/ai/ai-ask` | AI助手 | AI 医疗问答助手，恢复最近会话、发送问题、轮询回答/思考过程。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/ai-appointment/01-ai-appointment` | AI导诊 | AI 导诊对话页，结构化采集需求、追问补全、确认时间并启动匹配；日期和选项选择使用自定义底部选择面板。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/ai-appointment/02-ai-match-result` | AI推荐结果 | AI 推荐结果页，展示匹配陪诊师，选择陪诊师并创建订单；评分使用评价聚合结果，无评价显示“暂无评分”。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/profile/attendant-detail` | 陪诊师主页 | 陪诊师主页，展示公开资料/评分/资质；平均分支持半星，无评价显示“暂无评分/暂无评价”；资质状态和查看完整资质共用同一套可展示图片判断，优先原图、缺失时兜底扫描预览图。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/profile/edit-profile` | 编辑个人资料 | 用户编辑个人资料和头像；头像使用独立圆角外壳承载描边与阴影，避免头像图片本身为圆角矩形时出现圆形阴影。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/profile/avatar-crop` | 裁剪头像 | 头像裁剪工具页，裁剪后返回上传流程；正方形头像裁剪区只保留圆角矩形边框，不绘制圆形遮罩阴影。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/profile/id-card-crop` | 身份证框选 | 身份证正反面上传前的自定义框选页，按身份证比例裁切并输出清晰审核图。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/profile/edit-escort` | 编辑资料 | 陪诊师编辑个人资料、擅长领域、医院和简介。 | `/attendant/profile/{...}` |
| `subpkg/profile/withdraw-center` | 提现中心 | 提现中心入口，跳转钱包明细的提现 Tab。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/profile/wallet-detail` | 钱包明细 | 陪诊师钱包明细，优先使用后端 `attendantIncomeAmount` 展示真实收入；只展示实际到账金额大于 0 的收入记录；退款订单按 `settlementAmount` 结算，取消/超时关闭订单不产生收入。 | `/attendant/orders`<br>`/attendant/withdraw/records`<br>`/attendant/withdraw/apply` |
| `subpkg/profile/qualification` | 资质管理 | 陪诊师资质管理，按状态总览、四项材料清单、审核记录三段展示；资质状态只表达待补充、审核中、已通过、未通过、证件过期，账号禁用不混入资质状态；驳回原因置顶高亮，已通过时不展示提交审核按钮。 | `/attendant/profile/{...}`<br>`/attendant/qualification/{...}/submit` |
| `subpkg/profile/qualification-upload` | 上传资质 | 上传/更新身份证、执业证、健康证和证件有效期；页面按身份证、执业证、健康证三块分步展示，外层显示扫描/框选预览，点击查看原图；身份证框选导出和普通证件图片都会先压缩再上传，避免大图直接触发上传失败；底部固定保存和提交审核，材料不完整时页面内提示原因，并优先透传后端返回的失败原因。 | `/attendant/qualification/{...}`<br>`/attendant/qualification/{...}/submit`<br>`/api/common/upload-image` |
| `subpkg/profile/reviews` | 我的评价 | 陪诊师评价列表，查看订单评价并回复用户评价；平均评分无评价时显示“暂无评分”。 | `/attendant/orders`<br>`/attendant/orders/` |
| `subpkg/profile/service-stats` | 服务统计 | 陪诊师服务统计，汇总已完成订单、收入和按评价数展示的好评率。 | `/attendant/orders` |
| `subpkg/profile/platform-rules` | 平台规则 | 平台规则说明页。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/profile/service-center` | 客服中心 | 客服中心入口，跳转消息/联系支持。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/profile/help` | 帮助中心 | 帮助中心常见问题。 | 无直接接口调用/通过封装模块调用 |
| `subpkg/profile/settings` | 设置 | 设置页，通知开关、缓存清理和关于平台；缓存清理和关于信息使用自定义底部弹窗。 | 无直接接口调用/通过封装模块调用 |

## 管理端页面

| 路由 | 组件 | 权限 | 核心交互逻辑 | 主要接口 |
| --- | --- | --- | --- | --- |
| `/login` | `LoginPage.vue` | 公开/游客 | 管理员登录，保存 admin token 并按 redirect 跳转；支持微信扫码登录，未绑定微信时先账号密码登录完成绑定。 | `POST /api/admin/auth/login`<br>`GET /api/admin/auth/wechat/oauth-url`<br>`GET /api/admin/auth/current`<br>`POST /api/admin/auth/wechat/bind` |
| `/dashboard` | `DashboardPage.vue` | 管理员 JWT | 首页概览，用户指标拆分展示总用户、患者用户和陪诊师；指标卡不展示订单总量，只保留今日订单和争议订单，另有待审核/争议/今日订单快捷入口；超级管理员可见最近操作日志摘要。 | `GET /api/admin/dashboard/overview` |
| `/workbench` | `WorkbenchPage.vue` | 管理员 JWT | 处理工作台，统一流水线处理订单争议和陪诊师入驻审核；系统自动分配和续期任务、实时刷新队列、完成后进入下一条。指定 `targetId` 可直接领取处理，不因当前分页不包含该任务而丢失。订单争议证据区突出陪诊师提交实际时长、用户不认可原因和平台裁定去向；修改最终时长会按服务类型自动核算最终金额，管理员仍可手动覆盖金额；当前流程陪诊师只提交实际时长，不保存提交原因。陪诊师审核默认展示扫描预览图，放大查看展示用户上传原图。 | `GET /api/admin/workbench/summary`<br>`GET /api/admin/workbench/tasks`<br>`POST /api/admin/workbench/tasks/{type}/{targetId}/claim`<br>`POST /api/admin/workbench/tasks/{type}/{targetId}/complete`<br>`DELETE /api/admin/workbench/tasks/{type}/{targetId}/claim`<br>`GET /api/admin/orders/{id}`<br>`GET /api/admin/attendants/{id}` |
| `/users` | `UsersPage.vue` | 管理员 JWT | 用户筛选、分页、档案抽屉查看、启用/禁用用户。列表点击或查看资料直接打开右侧工作抽屉展示完整资料、资质与最近订单。 | `GET /api/admin/users`<br>`GET /api/admin/users/{id}`<br>`PATCH /api/admin/users/{id}/status` |
| `/attendants` | `AttendantsPage.vue` | 管理员 JWT | 陪诊师审核工作台，资质状态区分待补充、待审核、已通过、未通过，账号状态单独显示正常/禁用；只有陪诊师提交审核后的待审核记录才进入审核队列和工作台，待补充不显示审核操作。列表详情可直接通过/驳回/标记未通过/通过资质，也可进入处理工作台流水线。资质缩略图默认用扫描预览图，点开看原图。材料缺失或过期时禁用通过。超级管理员可看到审核人字段。 | `GET /api/admin/attendants`<br>`GET /api/admin/attendants/{id}`<br>`GET /api/admin/attendants/{id}/qualification-logs`<br>`PATCH /api/admin/attendants/{id}/qualification-review`<br>`PATCH /api/admin/attendants/{id}/status` |
| `/attendants/:id` | `AttendantDetailPage.vue` | 管理员 JWT | 陪诊师详情、历史订单、资质有效期、审核记录、资质通过/驳回/标记未通过、账号禁用/恢复；待补充状态只展示材料进度，不提供资质审核动作。资质缩略图默认用扫描预览图，点开看原图。超级管理员可看到审核人字段。 | `GET /api/admin/attendants/{id}`<br>`GET /api/admin/attendants/{id}/qualification-logs`<br>`PATCH /api/admin/attendants/{id}/qualification-review`<br>`PATCH /api/admin/attendants/{id}/status` |
| `/orders` | `OrdersPage.vue` | 管理员 JWT | 订单筛选、详情抽屉查看、取消订单、争议处理、确认退款完成。列表点击或查看记录直接打开右侧工作抽屉展示完整资料与处理操作，并同步 `selectedId` 查询参数；争议处理弹窗修改最终时长时自动重算最终金额，可再手动覆盖；退差价进入待平台退款，管理员确认已退款后订单完成。 | `GET /api/admin/orders`<br>`GET /api/admin/orders/{id}`<br>`PATCH /api/admin/orders/{id}/cancel`<br>`PATCH /api/admin/orders/{id}/dispute-resolution`<br>`PATCH /api/admin/orders/{id}/refund-complete` |
| `/orders/:id` | `OrderDetailPage.vue` | 管理员 JWT | 订单详情、取消订单、时长费用争议处理、确认退款完成；修改最终时长时自动重算最终金额，可再手动覆盖。 | `GET /api/admin/orders/{id}`<br>`PATCH /api/admin/orders/{id}/cancel`<br>`PATCH /api/admin/orders/{id}/dispute-resolution`<br>`PATCH /api/admin/orders/{id}/refund-complete` |
| `/system` | `SystemPage.vue` | 超级管理员 JWT | 管理员账号管理，创建管理员/超级管理员、启用/停用/删除账号。普通管理员不显示该入口，手动访问会静默回首页。 | `GET /api/admin/admin-users`<br>`POST /api/admin/admin-users`<br>`PATCH /api/admin/admin-users/{id}/status`<br>`DELETE /api/admin/admin-users/{id}` |
| `/logs` | `LogsPage.vue` | 超级管理员 JWT | 操作日志工作台，按模块、动作、管理员角色、关键词和时间范围分页查询后台处理记录；用户管理和陪诊师审核指向同一用户时，对象列统一显示“姓名（脱敏手机号）/ 用户 ID”。普通管理员不显示该入口，手动访问会静默回首页。 | `GET /api/admin/operation-logs` |

## 导航和鉴权

- 小程序使用原生 Tab/分包跳转，登录态由 `stores/session.js`、`stores/user.js` 和请求封装维护。APP-PLUS Android 在 `App.vue` 的 `onLaunch` 和 `onShow` 自动调用 `/api/app-upgrade/check`，发现 `101` WGT 更新后下载、安装并重启；H5/非 APP 环境不会执行安装。Android WGT 资源包启用 `app-plus.compatible.ignoreVersion`，避免服务器 HBuilderX 5.07 生成的 WGT 在 5.06 Runtime 安装包中反复弹出版本不一致提示；后续发布完整 APK 时再升级内置 Runtime。
- 陪诊师资质门禁弹窗只出现在接单厅入口和接单厅页面内：新注册陪诊师默认为待补充，可进入陪诊师端“我的”，点击接单厅时提示上传并提交资质；提交审核后变为待审核且不弹窗，待补充/未通过/证件过期同一前台会话同状态只提示一次；订单、消息、我的页面不自动弹资质提醒。
- 评分展示统一通过 `utils/rating.js`：平均星级必须同时传入 `score` 和 `evaluationCount`，好评率必须同时传入 `praiseRate` 和 `evaluationCount`；缺少评价数或评价数为 0 时统一显示“暂无评分/暂无评价”，避免把旧缓存或缺省值显示成 100%。
- 陪诊师收入展示统一通过 `utils/settlement.mjs`：钱包和已完成订单使用后端 `settlementAmount`、`platformFeeAmount`、`attendantIncomeAmount`；钱包收入列表过滤实际到账金额小于等于 0 的记录，并用“实际到账/按退款后结算”表达结算口径；待接单、专属派单和服务中页面只可展示预估收入；不得在页面内直接写 `orderAmount * 0.9`。
- 管理端使用 Vue Router，`meta.requiresAuth` 路由必须存在管理端 token；401 响应会清理会话并跳转 `/admin/login`。
- 管理端会在恢复本地会话时检查 JWT `exp`，过期 token 会直接清理并进入登录页；接口返回 401 时统一静默跳转登录页，不在后台页面残留“加载失败/登录失败”提示。
- 微信登录入口按运行环境显示：微信小程序使用 `uni.login`，微信内 H5 使用公众号网页授权，普通浏览器 H5 保留手机号密码登录；管理端使用微信开放平台网站应用扫码登录。App 微信登录代码路径已预留，但 WGT 不能给已安装 App 动态新增原生 OAuth 模块，需后续发布完整 APK/IPA 时启用对应原生模块。
- 管理端侧栏入口在 `frontend/admin/src/components/AppShell.vue` 中维护。
- 管理端浏览器标题为“愈安伴后台管理”，登录页、侧栏和 favicon 统一使用 `frontend/admin/public/brand-logo.png`。
- 管理端活动弹窗统一使用 `BaseDialog.vue` 和 `styles.css` 中的 `.dialog-*` 自定义样式，不使用浏览器原生确认框；列表详情使用 `BaseDrawer.vue` 和 `.drawer-*` 自定义工作抽屉，避免详情堆到页面底部；筛选、分页和管理员账号类型选择使用 `BaseSelect.vue` 自定义下拉，日期/时间筛选使用 `BaseDateInput.vue` 自定义日历浮层，避免浏览器原生下拉和日期弹窗样式。
- 小程序端业务流程弹窗统一使用页面内自定义面板/底部 Sheet；除隐藏版本调试入口外，不在注册、预约、订单、设置、退出登录、AI 导诊选择等主流程使用 `uni.showModal` 或原生 ActionSheet。

## 维护规则

- 本文件是可推送的前端页面和管理端路由清单；真实账号、密码、服务器连接信息不写入本文档。
- 小程序页面数量按 `frontend/mini-program/pages.json` 中主包 `pages` 与 `subPackages[].pages` 合计统计。
- 管理端路由页面数量按 `frontend/admin/src/router/index.js` 中实际页面组件路由统计，`/` 重定向不计入页面数量。
- 修改 `pages.json`、新增/删除小程序页面、改变核心跳转或接口时，必须同步更新“小程序端页面”。
- 修改 `frontend/admin/src/router/index.js`、`admin-api.js` 或管理端页面核心操作时，必须同步更新“管理端页面”。
