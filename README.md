Originally released on the Google Play Store, Bat Recorder is an Android app designed to record and analyze ultrasonic audio such as bat echolocation calls. I have retired from active code development, and as such I have provided the code for the app free to all under the GNU license - contributions that improve the app and fix bugs are very much desired and welcome.

The first incarnation of the app was written over a decade ago, which is why the code base is primarily in Java and not Kotlin. If someone would like to do a conversion, that would be great. Also, it would be ideal to have a suite of unit tests.

There was a known bug where the USB driver gets overwhelmed with data on certain devices, causing period skips in data acquisition. This has been fixed by Radek DiLuca of Dodotronic (www.dodtronic.com)! Much appreciated!

Note that to use this code base, you will need your own key for the Google Maps API.
