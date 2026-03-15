# CGL - Parent Manager Control Tool

## 功能

### SQL执行
- 利用ParentManager的SQL注入漏洞
- 支持执行任意SQL命令
- 查看执行结果

### 拦截控制
- **后台删除数据库**：定时清理家长管理的使用记录
- **VPN拦截请求**：拦截家长管理向服务器上传数据的请求

## 编译

使用GitHub Actions自动编译，或手动编译：

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## 使用

1. 安装APK
2. 打开应用
3. 选择功能：
   - SQL执行：输入SQL命令并执行
   - 拦截控制：选择拦截方法并启动服务

## 注意

- 需要Root权限或ParentManager已安装
- VPN拦截需要用户授权