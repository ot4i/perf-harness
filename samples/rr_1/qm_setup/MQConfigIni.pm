#
########################################################## {COPYRIGHT-TOP} ###
## Copyright 2023 IBM Corporation
##
## All rights reserved. This program and the accompanying materials
## are made available under the terms of the MIT License
## which accompanies this distribution, and is available at
## http://opensource.org/licenses/MIT
########################################################### {COPYRIGHT-END} ###
#
package MQConfigIni;

use strict;
use warnings;

use Carp;
use Data::Dumper;

use Exporter;

BEGIN
{
  our @ISA    = qw(Exporter);
  our @EXPORT = qw(readMQConfigFile writeMQConfigFile);
  our @EXPORT_OK;
}

#
# Read 0 or more MQ style .ini files (stanza: not [stanza])
# Merge them to produce two deep hash tree as follows
#
# $result ->{$stanza}{$property_name}=$property_value
#
# Each .ini file must start with a stanza: before specifying any properties.
#

sub readMQConfigFile
{
  my (@fns) = @_;

  my $result = {};

  foreach my $fn (@fns)
  {

    #
    # Must be a file and readable
    #

    croak "File '$fn' does not exist\n"  unless -f $fn;
    croak "File '$fn' is not readable\n" unless -r $fn;

    #
    #  Slurp the file
    #

    my $fh;
    open($fh, '<', $fn) or croak "Unable to open file $fn : $!\n";
    my @data = <$fh>;
    close $fh or croak "Unable to close file $fn : $!\n";

    #
    # Chomp the data (get rid of end of line characters)
    #

    chomp @data;

    my $stanza = q{};

  LINE: foreach (@data)
    {
      next LINE if m/^\s*#/;    # skip comments;
      next LINE if m/^\s*$/;    # skip blank lines;
                                #
      if (m/\s*(\w+):\s*$/)     # stanza:
      {
        $stanza = $1;
        next LINE;
      }

      if (my ($key, $value) = m/^\s*([^=]+?)\s*=\s*(.+?)\s*$/)
      {
        $result->{$stanza}{$key} = $value;
        next LINE;
      }
      carp "Unable to parse line '$_' from file '$fn'\n";
    }
  }

  return $result;
}

sub writeMQConfigFile
{
  my ($fn, $hash) = @_;

  my $fh;
  open($fh, '>', $fn) or croak("Unable to open file '$fh' for output: $!\n");

  #
  # Blank stanza name (default stanza) is supported.
  #
  # It will be listed first in the file with no stanza: line.
  #

STANZA: foreach my $stanza (sort keys %$hash)
  {
    print {$fh} "$stanza:\n"
      unless $stanza eq q{};

    my $props = $hash->{$stanza};
    my $spaces = ($stanza eq q{}) ? '' : '   ';

  PROPERTY: foreach my $key (sort keys %$props)
    {
      my $value = $props->{$key};
      print {$fh} "$spaces$key=$value\n";
    }
  }

  close $fh or croak("Unable to close file '$fh' for output: $!\n");
}

1;
