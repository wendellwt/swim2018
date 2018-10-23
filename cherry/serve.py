
# cygwin:
#      /cygdrive/c/Users/WTurner/Python37/python.exe serve.py

import cherrypy
import sys
import os

# Q: why is this commented out???
#cherrypy.config.update( "srv.conf" )

#======================================================
# new oct16: do d.b. connection here

#------------------------------------------------ dos, unix, or cygwin?

"""
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
    print("  cygwin:   /cygdrive/c/Python27/python.exe")
    print("  dos:      C:\Python27\python.exe")
    print("(on webfaction, this should have worked)")

    sys.exit(1)
#---------------------------------------------- end of dos, unix, or cygwin

print("111")
db_conn = psycopg2.connect( db_access )
print("222")
db_csr = db_conn.cursor()
print("333")
"""
db_csr = None

#======================================================

#-----------------
# my cgi (python3) programs are here:
# path is DOS path
sys.path.append("/cygwin64/home/WTurner/cherry/cgi")

import get_tracks
#------------------

PATH = os.path.abspath( os.path.dirname(__file__))

class Root(object):
    @cherrypy.expose
    @cherrypy.tools.json_out()
    def get_tracks_ajax( self, apt, random):
        print("ajax:", apt)

        gjson = get_tracks.ajax( db_csr, "stdds", apt )

        print("ajax: return" )
        return gjson
    get_tracks_ajax.exposed = True    # does this duplicate the @ decorator

cherrypy.tree.mount( Root(), '/', config={
    '/': {
        'tools.staticdir.on': True,
        'tools.staticdir.dir': PATH,
        'tools.staticdir.index': 'index.html',
    },
})

cherrypy.engine.start()
cherrypy.engine.block()

