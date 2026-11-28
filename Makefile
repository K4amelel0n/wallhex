
start:
	sh ./mvnw spotless:apply
	sh ./mvnw compile exec:java -Dexec.mainClass=wallhex.App

test:
	sh ./mvnw test
