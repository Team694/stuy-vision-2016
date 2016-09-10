Stuy Vision 2016
================

This was the beginning our CV project for FRC 2016: Stronghold. Work was
transferred to our robot [DEStiny](https://github.com/Team694/DEStiny)'s
repository when we chose to run the computer vision on the roboRIO, rather
than on a Jetson TK1.

Forked and made using an alumni's project, inspired by [CUAUV](https://github.com/cuauv)'s auv-vision-gui

## Setting Up

If JAVA_HOME is not set, set it with

```
$ export JAVA_HOME=/path/to/jdk
```

This should be in your ~/.bashrc or .profile to persist across
reboots. (Do not include the dollar sign.)

To install OpenCV 3.0, first update your packages:

```
$ sudo apt-get update
$ sudo apt-get upgrade
```

, then install OpenCV's dependencies. Running `install-opencv-unix.sh`
will show you the dependencies, then ask you whether to continue. Say
no and install them.

Then run:

```
$ ./install-opencv-unix.sh
```

### To use with eclipse

After OpenCV builds, update the Eclipse classpath:

```
$ cp .classpath-generic .classpath     # Generic JRE

OR

$ cp .classpath-osx .classpath         # OSX JRE-1.8 u65
```

### To build/run with ant
`ant compile` builds the project.

`ant run-tegra` runs the Tegra target (modules.StuyVisionModule.main) (starting
the Tegra server).  `ant compile-and-run-tegra`, or simply `ant`, compiles then
runs for the tegra.

`ant run-gui` runs the GUI (gui.Main.main). `ant compile-and-run-gui` compiles
then runs the GUI.

Make sure `jfxrt.jar` is in the right place. `build.xml` expects it at
`/usr/lib/jvm/jfxrt.jar`.

## Setting Up on a NVIDIA Jetson

To install OpenCV 3.0, run:

```
$ ./install-opencv-tegra.sh
```

To set up the runlevel and run the cv on boot, modify `/etc/init/rc-sysinit.conf` to use runlevel 4, which you will then modify

Find the line `env DEFAULT_RUNLEVEL=2`, and change it to
```
env DEFAULT_RUNLEVEL=4
```

What will run in runlevel 4 is determined by the contents of `/etc/rc4.d/`.
`rc4.d` contains symbolic links to the scripts that will run when booting to this run level.
Add a symbolic link to a script whose name starts with `S`, followed by a two-digit number,
and then a descriptive name. The two-digit number determines the order in which the scripts will run.

```
cd /etc/rc4.d/
sudo ln -s /path/to/script S80run-cv
```
