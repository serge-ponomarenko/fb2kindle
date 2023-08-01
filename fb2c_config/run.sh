#!/bin/bash
# $1 - user chatID
# $2 - user email
# $3 - input file path
# $4 - css profile
# $5 - margins

pwd
cd /home/ubuntu/fb2kindle/fb2c
cp configuration.toml configuration_$1.toml
cp profiles/$4 profiles/$1_$4
sed -i "s/emailtosend/$2/" configuration_$1.toml
sed -i "s=profiles/default.css=profiles/$1_$4=" configuration_$1.toml
sed -i "s/marginToSet/$5/" profiles/$1_$4
./fb2c --config configuration_$1.toml convert --to epub --ow --stk $3 ../data/outputFiles &
pid=$!
wait $pid
exitcode=$?
rm configuration_$1.toml
rm profiles/$1_$4
exit $exitcode