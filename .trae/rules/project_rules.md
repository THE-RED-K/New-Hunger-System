# New Hunger System 项目规则

## 构建命令(Windows)

系统未配置 JAVA_HOME,构建前必须显式指定 JDK 17(gradle 缓存路径):

```powershell
$env:JAVA_HOME="C:\Users\Illus\.gradle\jdks\eclipse_adoptium-17-amd64-windows\jdk-17.0.19+10"
.\gradlew.bat build --console=plain
```

- 产物: `build\libs\new_hunger_system-1.0.0-1.20.1.jar`
- 反编译定位工具: 同 JDK 路径下的 `bin\javap.exe`

## 部署到测试实例

测试实例: `D:\A.Minecraft(MC)\.minecraft\versions\1.20.1-Forge_47.4.23-Test`

```powershell
$mods="D:\A.Minecraft(MC)\.minecraft\versions\1.20.1-Forge_47.4.23-Test\mods"
Copy-Item "$mods\new_hunger_system-1.0.0-1.20.1.jar" "$mods\new_hunger_system-1.0.0-1.20.1.jar.bak" -Force
Copy-Item "build\libs\new_hunger_system-1.0.0-1.20.1.jar" "$mods\new_hunger_system-1.0.0-1.20.1.jar" -Force
```

## 已知诊断约定

- 探针日志 logger: `new_hunger_system.diag`,输出前缀 `[DIAG]`
- 测试实例日志: `D:\A.Minecraft(MC)\.minecraft\versions\1.20.1-Forge_47.4.23-Test\logs\latest.log`