[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path $PSScriptRoot -Parent
$previousJavaHome = $env:JAVA_HOME
try {
    $selectedJdk = @($env:JAVA_HOME, 'C:\Program Files\Java\jdk-21', 'C:\Program Files\Java\jdk-17') |
        Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'release')) -and
            (Get-Content -LiteralPath (Join-Path $_ 'release') -Raw) -match 'JAVA_VERSION="(17|21)\.' } |
        Select-Object -First 1
    if (!$selectedJdk) { throw 'Can JDK 17 hoac 21 de chay kiem thu.' }
    $env:JAVA_HOME = $selectedJdk
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $mavenPath = if ($maven) { $maven.Source } else { Join-Path $workspacePath 'com_be/mvnw.cmd' }
    & $mavenPath -f (Join-Path $workspacePath 'com_be/pom.xml') `
        "-Dtalent.build.directory=$(Join-Path $workspacePath '.local-run/admin-test')" `
        '-Dspring.datasource.url=jdbc:h2:mem:regression;MODE=MSSQLServer;DB_CLOSE_DELAY=-1' `
        '-Dspring.datasource.driver-class-name=org.h2.Driver' '-Dspring.datasource.username=sa' '-Dspring.datasource.password=' `
        '-Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect' `
        '-Dspring.jpa.hibernate.ddl-auto=create-drop' '-Dapp.seed.enabled=false' test
    $testExitCode = $LASTEXITCODE
} finally { $env:JAVA_HOME = $previousJavaHome }
exit $testExitCode
