import os
import re

###############################################################################################################
# generatePropertiesSummary.py
# Generates a compilation of all the properties files, for quick reference.
# E.g. python generatePropertiesSummary.py > propertiesSummary.txt
###############################################################################################################

properties = {}

def store_attribute(className, key1, key2, value):
    #print("Key1: {}  Key2: {}  Value:{}".format(key1,key2,value))
    if not key1 in properties:
        properties[key1] = {}
    properties[key1][key2] = value
   
def print_attributes():
    if properties:
        for prop_name, prop_attributes in properties.items():
            print("{}\t{}\n\t Type ={} (default: {})".format(prop_name, prop_attributes['desc'], prop_attributes['type'], prop_attributes['dflt']))
        if 'xtra' in prop_attributes:
            print("      {}".format(prop_attributes["xtra"]))
    else:
        print("No properties for this class")


mypath = os.path.normpath("../..")
f = []

regex_dict = {
    #Lazy '?' matches used, to stop regex matching on other properties sometimes
    #quoted in a property's description
    'key1key2Value': re.compile(r'(.+?)\.(.+?)=([\S\s]+)'),
   
}
k=0
exclude = set(["bin"])
delineator="\n+---------------------------------------------------------------------------------------------------------------------------------+\n"
for (dirpath, dirnames, filenames) in os.walk(mypath):
    dirnames[:] = [d for d in dirnames if d not in exclude]
    for fileName in filenames:  
        if re.search("\.properties$",fileName) and "build" not in fileName:
            #print("File: {}".format(os.path.normpath(dirpath+"/"+fileName)))
            className=fileName.split('.')[0]
            with open(os.path.join(dirpath, fileName)) as file:
                line = file.readline()  
                while line:
                    #Join multilines
                    while re.search("\\\$",line):
                        line = line.rstrip("\\\nn") + " " + file.readline()
                        #print("Multiline: {}".format(line))
                    match = re.match("com.ibm.uk.hursley.+\.(.+).desc.?=([\S\s]+)",line)
                    if match:
                        if match.group(1) != className:
                            print("Warning: Class described in properties file ({}) does not match property file name {}".format(match.group(1),os.path.normpath(dirpath+"/"+fileName)))
                        print("{}\n{}".format(match.group(1), match.group(2)))
                    else:
                        match = regex_dict['key1key2Value'].search(line)
                        if match:
                            #print("line({}): {} ".format(k,line))
                            store_attribute(className, match.group(1).rstrip(),match.group(2).rstrip(),match.group(3).rstrip("\\\n"))
                            k=k+1
                    line = file.readline()
            file.close()
            print_attributes()
            properties={}
            print(delineator)
