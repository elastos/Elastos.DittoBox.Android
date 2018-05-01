Elastos.PersonalCloudDrive.Android
==================================

## Introduction

Elastos personal cloud drive (ElaDrive) is a demo application integrating ownCloud over elastos carrier network, and through which we can access or save personal files to ownCloud server that could be deployed **at home behind the router**.

## Highlights

This app demonstrates that all traditional http(/https)-based application can be refactored to elastos carrier apps running over carrier network. Being elastos carrier web app, the app server can be deployed without the limit of direct network accessible.

For example, through elastos carrier network, you can deploy ownCloud server at local network at your home, and access ownCloud service at anywhere.

## Build from source

Run following commands to get full source code:

```shell
$ git clone --recurse-submodules git@github.com:elastos/Elastos.PersonalCloudDrive.Android.git
```

or

```shell
$ git clone git@github.com:elastos/Elastos.PersonalCloudDrive.Android.git
$ git submoudle update --init --recursive
```

Then open this project with Android Studio to build distribution.

## Dependencies

### 1. ownCloud

See details for ownCloud in **README.ownCloud.md**.

### 2. Elastos Carrier

See details for elastos carrier in **https://github.com/elastos/Elastos.NET.Carrier.Android.SDK**, and build **org.elastos.carrier-debug(or release).aar** with instructions.

## Deployment

Before to run ElaDrive on Android, you need to have ElaDrive service to connect with. About how to build and install ownCloud server and personal cloud drive agent, please refer to instructions in following repository:

```
https://github.com:elastos/Elastos.PersonalCloudDrive.Service
```

## Run

After build and installation of ElaDrive on Android, you need to scan QRcode of ElaDriver agent address to pair at first. When pairing server succeeded, then you can use ownCloud to access and save files as origin ownCloud does.

Notice: Due to carrier is decentralized network, there would be a moment about 5~30s for ElaDriver app completely connected to carrier network.

## Thanks

All works bases on ownCloud and elastos carrier Android SDK. Thanks to ownCloud team (especially) and carrier team.

## License

GPLv3

