import { defineConfig } from 'vitepress'

const DOC_VERSION = '0.32'
const DOC_LABEL = 'JNative 0.32 / CLI 1.0-dev'

export default defineConfig({
  title: 'JNative 开发者文档',
  description: 'Java 风格的独立原生语言：安装、语言、API、桌面 / Web / 移动开发指南',
  lang: 'zh-CN',
  cleanUrls: true,
  lastUpdated: true,
  ignoreDeadLinks: [
    /^https?:\/\/localhost/,
    /\.\.\/\.\.\/examples\//,
  ],
  head: [
    ['meta', { name: 'theme-color', content: '#1a2332' }],
  ],
  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'JNative',
    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: { buttonText: '搜索', buttonAriaLabel: '搜索文档' },
              modal: {
                noResultsText: '未找到结果',
                resetButtonTitle: '清除',
                footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' },
              },
            },
          },
        },
      },
    },
    nav: [
      { text: '入门', link: '/guide/what-is-jnative' },
      { text: '语言', link: '/language/' },
      { text: 'API', link: '/api/' },
      { text: '语言教程', link: '/tutorial/' },
      { text: '应用教程', link: '/tutorials/' },
      { text: '示例', link: '/samples/' },
      {
        text: DOC_LABEL,
        items: [
          { text: '版本与兼容', link: '/reference/versions' },
          { text: '支持矩阵', link: '/reference/support-matrix' },
          { text: '迁移指南', link: '/reference/migration' },
          { text: '事实清单', link: '/reference/fact-inventory' },
          { text: '阶段交付', link: '/reference/stages' },
        ],
      },
    ],
    sidebar: {
      '/tutorial/': [
        {
          text: '语言教程',
          items: [
            { text: '教程入口', link: '/tutorial/' },
            { text: '章节对应表', link: '/tutorial/MAPPING' },
            { text: '限制清单', link: '/tutorial/LIMITS' },
            { text: '验证报告', link: '/tutorial/VERIFICATION' },
          ],
        },
        {
          text: '章节',
          items: [
            { text: '第 01 章：JNative 的定位、编译与运行', link: '/tutorial/chapters/01-jnative-是什么' },
            { text: '第 02 章：安装及 VS Code 配置', link: '/tutorial/chapters/02-安装与-vscode' },
            { text: '第 03 章：Hello World', link: '/tutorial/chapters/03-hello-world' },
            { text: '第 04 章：项目目录、入口方法和输出', link: '/tutorial/chapters/04-项目与入口' },
            { text: '第 05 章：如何读懂编译错误', link: '/tutorial/chapters/05-编译错误' },
            { text: '第 06 章：变量、类型、赋值及作用域', link: '/tutorial/chapters/06-vars-types' },
            { text: '第 07 章：整数、浮点、布尔及数值边界', link: '/tutorial/chapters/07-numbers' },
            { text: '第 08 章：字符串、Unicode 与编码', link: '/tutorial/chapters/08-strings' },
            { text: '第 09 章：运算符、类型转换和求值顺序', link: '/tutorial/chapters/09-operators' },
            { text: '第 10 章：if、循环、switch', link: '/tutorial/chapters/10-control' },
            { text: '第 11 章：方法、参数、返回值与递归', link: '/tutorial/chapters/11-methods' },
            { text: '第 12 章：数组及增强 for', link: '/tutorial/chapters/12-arrays' },
            { text: '第 13 章：class、对象、字段及构造器', link: '/tutorial/chapters/13-objects' },
            { text: '第 14 章：封装与访问控制', link: '/tutorial/chapters/14-access' },
            { text: '第 15 章：继承、接口及动态分派', link: '/tutorial/chapters/15-inherit' },
            { text: '第 16 章：enum、final', link: '/tutorial/chapters/16-enum-final' },
            { text: '第 17 章：多文件、package/import', link: '/tutorial/chapters/17-multifile' },
            { text: '第 18 章：第三方源码包', link: '/tutorial/chapters/18-packages' },
            { text: '第 19 章：泛型及当前支持边界', link: '/tutorial/chapters/19-generics' },
            { text: '第 20 章：集合', link: '/tutorial/chapters/20-collections' },
            { text: '第 21 章：Lambda、闭包和方法引用', link: '/tutorial/chapters/21-lambda' },
            { text: '第 22 章：异常、finally 和资源管理', link: '/tutorial/chapters/22-exceptions' },
            { text: '第 23 章：文件读写', link: '/tutorial/chapters/23-files' },
            { text: '第 24 章：async/await、线程及取消', link: '/tutorial/chapters/24-async' },
            { text: '第 25 章：FFI、原生资源和安全边界', link: '/tutorial/chapters/25-ffi' },
            { text: '第 26 章：VS Code 补全、诊断及导航', link: '/tutorial/chapters/26-vscode-ide' },
            { text: '第 27 章：断点、单步、变量和调用栈', link: '/tutorial/chapters/27-debug' },
            { text: '第 28 章：测试与问题复现', link: '/tutorial/chapters/28-testing' },
            { text: '第 29 章：内存、GC 和资源生命周期', link: '/tutorial/chapters/29-gc-memory' },
            { text: '第 30 章：综合项目与后续学习路径', link: '/tutorial/chapters/30-capstone' }
          ],
        },
      ],
      '/guide/': [
        {
          text: '入门指南',
          items: [
            { text: 'JNative 是什么', link: '/guide/what-is-jnative' },
            { text: '运行依赖', link: '/guide/runtime-deps' },
            { text: '安装 SDK', link: '/guide/install' },
            { text: '检查工具链', link: '/guide/doctor' },
            { text: 'VS Code 插件', link: '/guide/vscode' },
            { text: '第一个项目', link: '/guide/first-project' },
            { text: '构建、运行与调试', link: '/guide/build-run-debug' },
            { text: '补全、诊断与日志', link: '/guide/diagnostics' },
            { text: '第三方源码包', link: '/guide/packages' },
            { text: '常见安装问题', link: '/guide/troubleshooting-install' },
          ],
        },
      ],
      '/language/': [
        {
          text: '语言开发指南',
          items: [
            { text: '概述与约定', link: '/language/' },
            { text: '文件、模块与可见性', link: '/language/modules' },
            { text: '基础类型与变量', link: '/language/types' },
            { text: '数值运算', link: '/language/numbers' },
            { text: '字符串与 Unicode', link: '/language/strings' },
            { text: '控制流', link: '/language/control-flow' },
            { text: '类、继承与接口', link: '/language/classes' },
            { text: '枚举与 final', link: '/language/enums' },
            { text: '泛型', link: '/language/generics' },
            { text: '集合与遍历', link: '/language/collections' },
            { text: 'Lambda 与方法引用', link: '/language/lambdas' },
            { text: '异常与资源', link: '/language/exceptions' },
            { text: '异步与线程', link: '/language/async' },
            { text: 'FFI 与原生内存', link: '/language/ffi' },
            { text: 'GC 与线程安全', link: '/language/gc' },
            { text: '限制与路线', link: '/language/limits' },
          ],
        },
      ],
      '/api/': [
        {
          text: 'API 参考',
          items: [
            { text: '索引与提取流程', link: '/api/' },
            { text: '基础与字符串', link: '/api/basics' },
            { text: '数值与集合', link: '/api/collections' },
            { text: '文件与流', link: '/api/io' },
            { text: '时间', link: '/api/time' },
            { text: '网络与 HTTP', link: '/api/net-http' },
            { text: '数据库', link: '/api/db' },
            { text: '并发与异步', link: '/api/async' },
            { text: '桌面 UI', link: '/api/ui' },
            { text: '浏览器与 WebUI', link: '/api/webui' },
            { text: '移动能力', link: '/api/mobile' },
            { text: 'FFI', link: '/api/ffi' },
          ],
        },
      ],
      '/tutorials/': [
        {
          text: '教程',
          items: [
            { text: '课程总览', link: '/tutorials/' },
            { text: 'Hello（第 0 课）', link: '/tutorials/hello/' },
          ],
        },
        {
          text: '桌面：任务管理器',
          items: [
            { text: '概览', link: '/tutorials/desktop/' },
            { text: '01 创建窗口', link: '/tutorials/desktop/lessons/01' },
            { text: '02 布局控件', link: '/tutorials/desktop/lessons/02' },
            { text: '03 表单校验', link: '/tutorials/desktop/lessons/03' },
            { text: '04 列表 CRUD', link: '/tutorials/desktop/lessons/04' },
            { text: '05 事件闭包', link: '/tutorials/desktop/lessons/05' },
            { text: '06 本地存储', link: '/tutorials/desktop/lessons/06' },
            { text: '07 后台任务', link: '/tutorials/desktop/lessons/07' },
            { text: '08 错误展示', link: '/tutorials/desktop/lessons/08' },
            { text: '09 调试', link: '/tutorials/desktop/lessons/09' },
            { text: '10 打包运行', link: '/tutorials/desktop/lessons/10' },
          ],
        },
        {
          text: 'Web：任务管理器',
          items: [
            { text: '概览', link: '/tutorials/web/' },
            { text: '01 项目结构', link: '/tutorials/web/lessons/01' },
            { text: '02 编译预览', link: '/tutorials/web/lessons/02' },
            { text: '03 页面状态', link: '/tutorials/web/lessons/03' },
            { text: '04 事件表单', link: '/tutorials/web/lessons/04' },
            { text: '05 共享核心', link: '/tutorials/web/lessons/05' },
            { text: '06 持久化', link: '/tutorials/web/lessons/06' },
            { text: '07 异步错误', link: '/tutorials/web/lessons/07' },
            { text: '08 JS 互操作', link: '/tutorials/web/lessons/08' },
            { text: '09 内存句柄', link: '/tutorials/web/lessons/09' },
            { text: '10 构建部署', link: '/tutorials/web/lessons/10' },
          ],
        },
        {
          text: '移动：任务管理器',
          items: [
            { text: '概览', link: '/tutorials/mobile/' },
            { text: '01 项目结构', link: '/tutorials/mobile/lessons/01' },
            { text: '02 工具链', link: '/tutorials/mobile/lessons/02' },
            { text: '03 容器', link: '/tutorials/mobile/lessons/03' },
            { text: '04 本地资源', link: '/tutorials/mobile/lessons/04' },
            { text: '05 安全区触摸', link: '/tutorials/mobile/lessons/05' },
            { text: '06 生命周期', link: '/tutorials/mobile/lessons/06' },
            { text: '07 桥接', link: '/tutorials/mobile/lessons/07' },
            { text: '08 本地存储', link: '/tutorials/mobile/lessons/08' },
            { text: '09 模拟器设备', link: '/tutorials/mobile/lessons/09' },
            { text: '10 调试打包', link: '/tutorials/mobile/lessons/10' },
          ],
        },
      ],
      '/samples/': [
        {
          text: '示例',
          items: [
            { text: '示例索引', link: '/samples/' },
            { text: '跨平台数独', link: '/samples/sudoku' },
          ],
        },
      ],
      '/reference/': [
        {
          text: '参考',
          items: [
            { text: '版本说明', link: '/reference/versions' },
            { text: '支持矩阵', link: '/reference/support-matrix' },
            { text: '迁移指南', link: '/reference/migration' },
            { text: '调试与故障排查', link: '/reference/debugging' },
            { text: '事实清单', link: '/reference/fact-inventory' },
            { text: '阶段交付', link: '/reference/stages' },
            { text: '文档验证', link: '/reference/verification' },
          ],
        },
      ],
    },
    outline: { label: '本页目录', level: [2, 3] },
    docFooter: {
      prev: '上一篇',
      next: '下一篇',
    },
    lastUpdatedText: '最后更新',
    darkModeSwitchLabel: '主题',
    sidebarMenuLabel: '菜单',
    returnToTopLabel: '回到顶部',
    footer: {
      message: `文档对应 ${DOC_LABEL} · 非 Java SE / 非 JAR 兼容`,
      copyright: '作者：张晓达',
    },
    editLink: {
      pattern: 'https://github.com/itcrown2010/jnative/edit/main/website/:path',
      text: '在仓库中编辑此页',
    },
  },
  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
    },
    en: {
      label: 'English',
      lang: 'en-US',
      link: '/en/',
      themeConfig: {
        nav: [{ text: 'Chinese docs (primary)', link: '/' }],
        sidebar: {},
      },
    },
  },
})
