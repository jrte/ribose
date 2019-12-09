You will need ginr v2.0.2 from googlecode to build jrte. Ginr can be downloaded from https://github.com/ntozubod/ginr.

After unpacking the tarball, to build ginr, 

```
	mkdir -p ~/bin 
	cd .../ginr-2.0.2/src
	make -f Makefile install
```	

This will install the ginr executable in ~/bin. ginr must be on your search PATH before building jrte.

To build jrte, 

```
	cd .../jrte
	ant -f build.xml all-clean
```	

See Javadoc at .../jrte/doc/index.html and .../jrte/etc/sh/*.sh for examples showing how to run simple transductions.

See LICENSE for licensing details.