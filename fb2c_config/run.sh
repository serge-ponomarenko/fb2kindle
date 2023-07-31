#!/bin/bash
pwd
cd /home/ubuntu/fb2kindle/fb2c
cp configuration.toml configuration_$1.toml
sed -i "s/emailtosend/$2/" configuration_$1.toml
sed -i 's=profiles/default.css=$4=' configuration_$1.toml
./fb2c --config configuration_$1.toml convert --to epub --ow --stk $3 ../data/outputFiles &
pid=$!
wait $pid
exitcode=$?
rm configuration_$1.toml
exit $exitcode