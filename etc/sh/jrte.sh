#! /bin/bash
if (($#==0)); then
	echo -e "Usage:\tjrte [<vm-arg ...] [--nil] <transducer-name> [<gearbox-path>] [<input-path> ...]\n\tDefault gearbox is build/patterns/Jrte.gears; stdin is default input path"
fi

gearbox="build/patterns/Jrte.gears"
vmargs=""
nil=""
while [[ "$1" =~ ^[-][^-] ]]; do
	vmargs="$vmargs $1";
	shift
done
if [[ "$1" == "--nil" ]]; then
	nil=$1
	shift
fi
if (($#>0)); then
	transducer=$1
	shift
fi
if (($#>0)) && [[ "$1" =~ gears$ ]]; then
	gearbox=$1
	shift
fi
if (($#==0)); then
	java $vmargs -cp jars/jrte-HEAD.jar com.characterforming.jrte.Jrte $nil $transducer $gearbox <&0
else
	cat $@ | java $vmargs -cp jars/jrte-HEAD.jar com.characterforming.jrte.Jrte $nil $transducer $gearbox
fi
