DittoBox for Android
==================================

## Introduction

DittoBox is a demo application integrating ownCloud over elastos carrier network, and through which we can access or save personal files to ownCloud server that could be deployed **at home behind the router**.

## Highlights

This app demonstrates that all traditional http(/https)-based application can be refactored to elastos carrier apps running over carrier network. Being elastos carrier web app, the app server can be deployed without requirement of direct network accessibility.

For example, through elastos carrier network, you can deploy ownCloud server at local network at your home, and access ownCloud service at anywhere.

## Build from source

Run following commands to get full source code:

```shell
$ git clone --recurse-submodules git@github.com:elastos/Elastos.DittoBox.Android.git
```

or

```shell
$ git clone git@github.com:elastos/Elastos.DittoBox.Android.git
$ git submodule update --init --recursive
```

Then open this project with Android Studio to build distribution.

## Dependencies

### 1. ownCloud

See details for ownCloud in **README.ownCloud.md**.

### 2. Elastos Carrier

See details for elastos carrier in **https://github.com/elastos/Elastos.NET.Carrier.Android.SDK**, and build **org.elastos.carrier-debug(or release).aar** with instructions.

## Deployment

Before to run DittBox on Android, you need to have DittBox service to connect with. About how to build and install ownCloud server and DittBox agent, please refer to instructions in following repository:

```
https://github.com/elastos/Elastos.DittoBox.Server
```

## Run

After build and installation of DittoBox on Android, you need to scan QRcode of DittoBox agent address to pair at first. When pairing server succeeded, then you can use ownCloud to access and save files as origin ownCloud does.

Beaware, due to carrier is decentralized network, there would be a moment about 5~30s for DittoBox app to get completely connected to carrier network, as well as get friends connected(or online).

## Thanks

All works base on ownCloud and elastos carrier Android SDK. Thanks to ownCloud team (especially) and carrier team.

## License

GPLv2

