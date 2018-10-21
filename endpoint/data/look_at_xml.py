#!/usr/bin/python

# read filenames listed in command line and pretty-print them in xml

import sys
from bs4 import BeautifulSoup

for filename in sys.argv[1:]:
    with open(  filename, 'r') as f:
        t = f.read()

        soup = BeautifulSoup(t, 'html.parser')

        print "<!-- ", filename, " -->"
        print(soup.prettify())
