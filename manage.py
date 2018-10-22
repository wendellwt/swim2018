
# this has been cloned with
#    git clone https://github.com/wendellwt/swim2018.git

#  /cygdrive/c/Users/wturner/Python37/python.exe  manage.py

# run this on asdi and check if java swim pgm and
#   cherrypy web server are running

import os
import sys
import signal
import psutil
import subprocess
import time
import datetime

#------------------------------------------------------

proc_info = {
    "java"   : "com/harris/gcsd/dex/jumpstart/CssiConsumer",
    "server" : "serve.py"
}

#----------------------------------------------------
#----------------  look for pids   ------------------
#----------------------------------------------------

def look_for_pids():

    for proc in proc_info:

        pid = find_by_cmd_line( proc_info[proc] )
        if pid is not None:
            print( proc, "pid is", pid )
        else:
            print( proc, "is not running")

#------------------------------------------------------

def find_by_cmd_line( cmd_arg ):

    for p in psutil.process_iter():

        try:
            this_is_it = None
            for arg in p.cmdline():
                if arg == cmd_arg:
                    return p
        except:
            #print("e:", "except!")
            pass

    return None   # fail
#----------------------------------------------------
#----------------  show pid   ------------------
#----------------------------------------------------

def some_info( p ):

    print()
    print("p=", p)
    for c in p.cmdline():
        print( "arg=", c )
    print( "pid=", p.pid  )
    print( "status=", p.status()  )
    print( "cpu_times=", p.cpu_times()  )
    print( "io_counters=", p.io_counters()  )
    for c in p.connections():
        print( "conn=", c )
    print()

#----------------------------------------------------
#----------------  kill pid   ------------------
#----------------------------------------------------

def kill_proc( pid ):

    # seems to kill process, but also gets a SystemError
    try:
        result = os.kill( pid, signal.CTRL_C_EVENT )
        print(result)
    except:
        print("exception!")

    #-----------------
    #result = os.kill( proc, signal.CTRL_BREAK_EVENT )
    #   PCT    1310  active     38.81583  -77.01832
    #   make: *** [makefile:5: run] Error 1
    #-----------------

#----------------------------------------------------
#----------------  start process ------------------
#----------------------------------------------------

strtw = "start /wait "
strt  = "start "

# ---- server

def start_server():
    cmd = strt + \
        "C:\\Users\\wturner\\Python37\\python.exe"  + \
          " serve.py"
    wd = "C:\cygwin64\home\wturner\cherry"

    print(cmd)
    print(wd)

    subprocess.call( cmd, shell=True, cwd=wd )

# ---- swim
# "'C:\Program Files (x86)\Common Files\Oracle\Java\javapath\java.exe'",

def start_swim():

    java_args = [
    # could not guess at the right way to escape spaces in dos dirnames, so
    # use dir /x to find out whant the 8-char name is
 "C:\progra~2\common~1\Oracle\Java\javapa~1\java.exe",
 "-Dlog4j.configuration=file:../lib/log4j.properties",
 "-cp",
 "../lib/wls/wlthint3client-10.3.6.jar;" +
    "../lib/activemq;" +
    "../lib/CSSIfirst.jar;" +
    "../lib/JumpstartKit.jar;" +
    "../lib/log;" +
    "../lib/log4j.properties;" +
    "../lib/postgresql-42.2.5.jar;" +
    "../lib/solace;" +
    "../lib/wls;" +
    "../lib/activemq/activemq-all-5.10.2.jar;" +
    "../lib/solace/sol-common-7.1.0.207.jar;" +
    "../lib/solace/sol-jcsmp-7.1.0.207.jar;" +
    "../lib/solace/sol-jms-7.1.0.207.jar;" +
    "../lib/log/commons-lang-2.6.jar;" +
    "../lib/log/commons-logging-1.1.3.jar;" +
    "../lib/log/log4j-1.2.16.jar;" +
    "../lib/log/slf4j-api-1.7.5.jar;" +
    "../lib/CSSIfirst.jar;" +
    ".",
 "com/harris/gcsd/dex/jumpstart/CssiConsumer",
 "../conf/consumerConnection.properties",
]

    cmd = strt + ' '.join(java_args)
    wd = "C:\cygwin64\home\wturner" + \
        "\swim\java\JumpstartKit-5.1.1\JumpstartKit\\bin"

    print()
    print("cmd=", cmd)
    print()
    print("wd=", wd)
    print()

    subprocess.call( cmd, shell=True, cwd=wd )

#----------------------------------------------------
#----------------  look at d.b.   ------------------
#----------------------------------------------------

#----------------------------------------------------- dos, unix, or cygwin?

try:
    # are we on unix or cygwin?
    un = os.uname()

    # if we got this far, then yes (unix or cygwin)
    # on webfaction:
    sys.path.append("/usr/lib64/python2.7/site-packages")  # where psycopg2 is
    sys.path.append("/home/wendell/lib/python2.7")         # where pytz is

    db_access = "dbname='cssitest' user='adminw' host='localhost' " + \
        " port='5432' password='shelikescarrots'"
except:
    # no, we must be on dos
    db_access = \
        "dbname='swim' user='postgres' host='asdi-db.cssiinc.com' " + \
        " port='5433' password='425thirdst'"
        #"dbname='cssitest' user='postgres' host='asdi-db.cssiinc.com' " + \
        #" port='5432' password='cssisuper'"

try:
    import psycopg2
except:
    print("on asdi: this MUST be run in DOS window (or using DOS python)")
    print("  cygwin:   /cygdrive/c/Python27 ???/python.exe")
    print("  dos:      C:\\Users\\wturner\\Python37\\python.exe")
    print("(on webfaction, this should have worked)")

    sys.exit(1)

# ------------------------------------

class MyPostGIS():
    def __init__(self, dba):
        self.db_conn = psycopg2.connect( dba )
        self.db_csr = self.db_conn.cursor()

    def get( self, q ):
        self.db_csr.execute(q)
        rows = self.db_csr.fetchall()
        return rows

    def close( self ):
        self.db_conn.close()

# ------------------------------------

#             table             timestamp column name
db_tables = { "rvr"       : "rvrtime",
             "fdps"       : "msg_arrival",
             "stdds"      : "mrttime",
             "flightplan" : None,
             "enhanced"   : None,
             "asdex"      : "time"        }

# connect now and make it global?
db = MyPostGIS( db_access )

# ------------------------------------

def query_db( ):

    for t in db_tables:
        if db_tables[t]:
            try:
                q = "select count(*), max(%s) from %s;" % (db_tables[t], t)
                rows = db.get( q )

                # Q: how to force gmt???
                #t = rows[0][1]
                #print(time.strftime("%b %d %Y %H:%M:%S", time.gmtime(t)) )
                print("%-15s  %8d   %s" % (t, rows[0][0],    \
                    rows[0][1].strftime("last: %m/%d/%Y %R %Z") ) )
            except:
                q = "select count(*) from %s;" % t
                rows = db.get( q )
                if len(rows) > 0:
                    print("%-15s  %8d" % (t, rows[0][0]))

        else:
            q = "select count(*) from %s;" % t
            rows = db.get( q )
            if len(rows) > 0:
                print("%-15s  %8d" % (t, rows[0][0]))

###############################################################

while(1):

    print()
    print("z or q == exit")
    print("l == look for pids")
    print("s <pid> == show info pid")
    print("k <pid> == kill pid")
    print("p == start cherry py server")
    print("w == start swim/java")
    print("d == count db tables")

    inp = input('Enter input: ')
    c = inp[0]

    #print("cmd is:", c)
    print()

    # ----- stop
    if (c == 'z') or (c == 'q'):
        break

    # ----- look
    if c == 'l':
        look_for_pids()

    # ----- show
    if c == 's':
        args = inp.split(' ')
        pid = int(args[1])
        p = psutil.Process(pid)
        some_info( p )

    # ----- kill
    if c == 'k':
        args = inp.split(' ')
        pid = int(args[1])
        #p = psutil.Process(pid)
        kill_proc( pid )

    # ----- cherry py
    if c == 'p':
        start_server()

    # ----- swim/java
    if c == 'w':
        start_swim()

    # ----- count db
    if c == 'd':
        query_db( )

