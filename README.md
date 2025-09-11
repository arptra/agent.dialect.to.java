# GigaChat Dialect → Java Transpiler (Java API, no REST)

Агент с 2 этапами (обучение по кодовой базе -> перевод), **без REST**, экспортирует чистый **Java API**.

## Зависимости
- JDK 17+
- Gradle
- Доступ к GigaChat через OpenAI-совместимый endpoint (`/v1/chat/completions`)

## Переменные окружения
```
export GIGACHAT_API_BASE=https://gigachat.sber.ru
export GIGACHAT_API_KEY=YOUR_KEY
export GIGACHAT_MODEL=gigachat
# optional mTLS auth
export GIGACHAT_CERT_FILE=
export GIGACHAT_KEY_FILE=
export GIGACHAT_CA_FILE=
```

Чтобы Gradle доверял сертификату `russiantrustedca.pem`, создайте truststore и укажите его в `gradle.properties`:

```bash
keytool -importcert \
    -file russiantrustedca.pem \
    -alias russian-ca \
    -keystore gradle-truststore.p12 \
    -storetype PKCS12 \
    -storepass changeit -noprompt
```

```
systemProp.javax.net.ssl.trustStore=gradle-truststore.p12
systemProp.javax.net.ssl.trustStorePassword=changeit
systemProp.javax.net.ssl.trustStoreType=PKCS12
```

## API (встраиваемый)
```java
import com.example.agent.api.TranslatorEngine;
import java.nio.file.Path;
import java.util.List;

var engine = TranslatorEngine.fromEnv(Path.of("runtime"));
engine.learn(Path.of("/path/to/repo"), List.of(".dlx",".dsl",".txt"));
String java = engine.translate("DECLARE x: INT; x := 10; print(x);");
System.out.println(java);
```

## CLI
```
./gradlew run --args="learn /path/to/repo .dlx,.dsl,.txt"
./gradlew run --args="translate samples/example.dlx"
./gradlew run --args="fix path/to/frag.dlx path/to/current.java path/to/feedback.txt"
```

Для включения подробного логирования команды `learn` установите `log=true` в `gradle.properties`.

## Как это работает
- `Learner` опрашивает GigaChat и собирает **правила** (regex+группы+шаблон Java) → `runtime/rules.jsonl`.
- `DynamicDialectParser` поднимает правила и строит IR (Assign/Call/Decl/If/Loop/Unknown).
- `TranslatorAgent` генерирует Java, компилирует и при успехе/исправлении через LLM **дообучает** правила.
- Кэш обработанных файлов — `runtime/processed_files.jsonl`.

## Пример файла диалекта
`samples/example.dlx`:
```
DECLARE x: INT;
x := 10;
print(x);
```


### Декларативные правила (без хардкода)
Правила теперь полностью управляют парсингом. Формат JSONL для `runtime/rules.jsonl`:
```
{"id":"assign1","irType":"Assign","regex":"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:=\s*(.+);\s*$","fields":["name","expr"]}
{"id":"call1","irType":"Call","regex":"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*\((.*)\)\s*;\s*$","fields":["callee","args"],"listFields":["args"]}
{"id":"decl1","irType":"Decl","regex":"^\s*DECLARE\s+([A-Za-z_][A-Za-z0-9_]*)\s*:\s*([A-Za-z0-9_<>\[\]]+)\s*;\s*$","fields":["name","type"]}
{"id":"if1","irType":"If","regex":"^\s*IF\s+(.+)\s+THEN\s*$","fields":["cond"]}
{"id":"loop1","irType":"Loop","regex":"^\s*FOR\s+(.+)\s+LOOP\s*$","fields":["header"]}
```
`DynamicDialectParser` подхватывает эти правила и через рефлексию создаёт нужные IR-ноды.
