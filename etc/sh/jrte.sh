#! /bin/bash
vmargs=""
while [[ "$1" =~ ^[-][^-] ]]; do
	vmargs="$vmargs $1";
	shift
done
if (($#<2)) || (($#>4)); then
	echo -e "Usage:\tjrte.sh [<vm-arg ...] [--nil] <transducer-name> <input-path> [<model-path>]"
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
if (($#>0)); then
	model="$1"
	shift
fi
if [[ -f "$input" && -f "$model" ]]; then
	java $vmargs -cp jars/ribose-0.0.0.jar com.characterforming.ribose.RiboseRuntime $nil $transducer $input $model
else
	if [[ ! -f "$input" ]]; then
		echo "No file $input"
	fi
	if [[ ! -f "$model" ]]; then
		echo "No model $model"
	fi
fi
