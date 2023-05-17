#!/bin/bash

head -n 250000 /dev/urandom | base64 > junk1.txt
head -n 500000 /dev/urandom | base64 > junk2.txt
head -n 1000000 /dev/urandom | base64 > junk3.txt