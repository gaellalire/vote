# Run

Install [vestige](https://gaellalire.fr/vestige)

## Run the demo

Install vote-demo in vestige and run it

## Run and configure each actor

*   Install and run rmiregistry
*   Install and run vote-state
*   Install and run vote-polling-station
*   Install and run vote-citizen

# Build

```
mvn clean install
```

# Generate Vestige descriptor

```
export VESTIGE_REPOSITORY_PATH=/path/to/vestige/repository
mvn clean deploy -Dvestige.application.version=1.0.0 -Pskip-deploy
```

# Publish

```
mvn clean release:prepare release:perform
```
