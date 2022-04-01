# /bin/bash
if (($#==4)); then
	if [[ "$1" == "--target" ]]; then
		target="$2"
	else 
		echo "Usage: jrtc --target <target-classname> <dfa-directory-path> <model-file-path>"
		echo "Compiles automata from <dfa-directory-path> into <model-file-path>."
		exit
	fi
	if [[ -d "$3" ]]; then
		dfadir="$3"
	else
		echo "Not a directory: $3"
		exit 1
	fi
	model="$4"
else 
	echo "Usage: jrtc --target <target-classname> <dfa-directory-path> <model-file-path>"
	echo "Compiles automata from <dfa-directory-path> into <model-file-path>."
	exit 1
fi	
java -cp jars/ribose-0.0.0.jar com.characterforming.ribose.RiboseRuntime --target $target "$dfadir" "$model"
