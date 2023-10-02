#!/usr/bin/perl
#
########################################################## {COPYRIGHT-TOP} ###
## Copyright 2023 IBM Corporation
##
## All rights reserved. This program and the accompanying materials
## are made available under the terms of the MIT License
## which accompanies this distribution, and is available at
## http://opensource.org/licenses/MIT
########################################################### {COPYRIGHT-END} ###
##
#  Contributors:
#     Various members of the WebSphere MQ Performance Team at IBM Hursley UK.
# *******************************************************************************/

# Merge 2 qm.ini files (2nd file overrides first)

use strict;
use warnings;

#use lib '.';

#use Carp;
#use File::Spec::Functions;
#use File::Temp;
use Getopt::Long;
use MQConfigIni;
#use Perf::Env;
#use Perf::MQEnv;

my ($qm_ini_file1,$qm_ini_file2) = @ARGV;
if( !defined($qm_ini_file1) || !defined($qm_ini_file1) ){die "You must supply two qm.ini files to be merged\n";}


my @fns = ($qm_ini_file1, $qm_ini_file2);

my $qm = readMQConfigFile(@fns);
writeMQConfigFile($qm_ini_file1, $qm);

print ("QM .ini file $qm_ini_file1 updated with contents of $qm_ini_file2\n");


