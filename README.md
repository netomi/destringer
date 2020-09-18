# DeStringer

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

A simple proof-of-concept to deobfuscate jar files protected by [Stringer](https://jfxstore.com/stringer/).

You can use the tool on the command-line like that:

```shell script
./gradlew run --args "obfuscated.jar deobfuscated.jar"
```

It will print the strings it could decrypt and store the result in the file ```deobfuscated.jar```.

License
-------
Code is under the [Apache Licence v2](https://www.apache.org/licenses/LICENSE-2.0.txt).
