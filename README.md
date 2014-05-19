Create ad-hoc network between iOS and android devices via Bluetooth LE.

### Features

- Advertise from iOS device / Discovery from Android device
- Bi-directional send/recv more than 20 bytes

### Demo

Tiny ac-hoc chat app built with this library.
Sending/receiving a photo is fake, since iOS device posts a photo to a web server and just sends that url to Android device :p

### Limitation

- It assumes 1 to 1 communication between iOS and Android devices. It can be extended to multiple devices with no hassle.
- Connection gets unstable sometimes.
