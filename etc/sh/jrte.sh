#! /bin/bash
vmargs=""
while [[ "$1" =~ ^[-][^-] ]]; do
	vmargs="$vmargs $1";
	shift
done
if (($#<2)) || (($#>4)); then
	echo -e "Usage:\tjrte.sh [<vm-arg ...] [--nil] <transducer-name> <input-path> [<gearbox-path>]\n\tDefault gearbox is build/patterns/Jrte.gears"
fi
nil=""
if [[ "$1" == "--nil" ]]; then
	nil=$1
	shift
fi
if (($#>0)); then
	transducer=$1
	shift
fi
if (($#>0)); then
	input="$1"
	shift
fi
gearbox="build/patterns/Jrte.gears"
if (($#>0)); then
	gearbox="$1"
	shift
fi
if [[ -f "$input" && -f "$gearbox" ]]; then
	java $vmargs -cp jars/jrte-HEAD.jar com.characterforming.jrte.Jrte $nil $transducer $input $gearbox
else
	if [[ ! -f "$input" ]]; then
		echo "No file $input
	fi
	if [[ ! -f "$gearbox" ]]; then
		echo "No gearbox $gearbox
	fi
fi
