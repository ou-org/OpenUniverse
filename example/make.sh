#!/bin/sh

rm -rf HelloUniverseRepo
mkdir HelloUniverseRepo

cp ../doc/examples/HelloUniverse.md HelloUniverseRepo/

cd HelloUniverseRepo
git init
git config user.name "Test User"
git config user.email "test@example.com"
git add .
git commit -m "Initial commit"

cd ..
rm HelloUniverse.zip
zip -r HelloUniverse.zip HelloUniverseRepo

