#!C:\Users\wturner\Python37\python.exe

#cygwin:
#     /cygdrive/c/Users/wturner/Python37/python.exe  get_tracks.py

# webfaction / ilc:
# pytz, and path

import sys
import os
import datetime
import json
from pprint import pprint

#==========================================================

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
#==========================================================
# > pip-2.7 install pytz (both asdi-db and webfaction)
# > pip-3.7 install pytz (asdi-db)

#ilc:sys.path.append("/home/wendell/lib/python2.7")   # ilc: where pytz is
import pytz        # webfaction needs sys.path fixed to get this

#--------------------

# define here so that these are global:
db           = None
mylg         = None

#ilc:go_back      = 4999
go_back      = 1   # search back this many minutes
                    # use 10 for fdps???
allow_tracks = 20   # allow this many tracks (debug)

#location     = "KIAD"  # fixed: is no longer global, is passed parameter

# <<<<<<<<<<<<<<<<< webfaction
#go_back      = 3555   # search back this many minutes
#allow_tracks = 4   # allow this many tracks

# ============================================================
# couldn't get cgi.log to work or even find examples, so just did my own...

class MyLog:

    def __init__(self, style):

        if style == "file":
            self.fn = "cgi_tailme_get_tracks.log"
            self.logdir = "C:/cygwin64/home/wturner/cherry/logs/"
            self.log    = open( self.logdir + self.fn, 'a')
            self.style  = "file"
        else:
            self.style  = "print"

    def hms(self):
        return datetime.datetime.now().strftime("%H:%M:%S") + ": "

    def write(self, wstr ):
        if self.style == "print":
            print(wstr)
        else:
            self.log.write(self.hms() + wstr + "\n" )

    def close(self ):
        if self.style == "print":
            self.log.close()

# ===============================================================

# oct16: mashed up from strict class that it was before
# now d.b. conn is established by cherrypy server

class MyPostGIS():
    def __init__(self, dba):
        self.db_conn = psycopg2.connect( dba )
        self.db_csr = self.db_conn.cursor()

    #ilc: def __init__(self, dbcsr):
        #self.db_csr = dbcsr

    def get( self, q ):
        mylg.write("g1")
        self.db_csr.execute(q)
        mylg.write("g2")

        try:
            rows = self.db_csr.fetchall()
            mylg.write("g3")
            return rows
        #except psycopg2.ProgrammingError:
        #    mylg.write("ProgrammingError")
        #    return None
        except:
            mylg.write("possible ProgErr")
            return None

    def close( self ):
        pass
    #    self.db_conn.close()

# ======================================================================

def get_then():

    # get current utc time
    unow = datetime.datetime.now( tz=pytz.utc )

    # Sherman, set the wayback machine to...
    way_back = datetime.timedelta( minutes=go_back )

    then = unow - way_back
    return then

# =============================

def query_recent_tracks_stdds(db, location):

    then = get_then()

    q = "select tracknum, lon, lat, vx, vy, reportedaltitude, mrttime " + \
        "from stdds " + \
        "where src_airport = '" + location + "' " + \
        "and mrttime is not null " +  \
        "and mrttime > '" + then.isoformat() + "' " + \
        "order by tracknum, mrttime ;"
        #"limit 29 ;"

    mylg.write("calling get")
    mylg.write(q)
    rows = db.get( q )
    mylg.write("get done")
    return rows

# =============================

def query_for_fplan_stdds( db, tracknum, location ):
    global mylg

    q = "select acid, actype, assignedbeaconcode, entryfix, exitfix " + \
        "from flightplan " + \
        "where tracknum = " + str(tracknum) + " " + \
        "and src_airport = '" + location + "' " + \
        "limit 1 ;"                  # there should be only one (!)

        #"where tracknum = '" + tracknum + "' " + \
        # <enhancedData> not in table at this moment...
        #" , eramgufi, sfdpsgufi " + \


    rows = db.get( q )

    if rows is not None:
        if len(rows) > 0:
            return rows[0] # s.b. only one!

    return None

# =============================

def query_recent_tracks_asdex(db, location):

    then = get_then()

    q = "select track, longitude, latitude, speed, heading, aircraftid " + \
        "from asdex " + \
        "where src = '" + location + "' " + \
        "and longitude is not null and latitude is not null " + \
        "and time is not null " +  \
        "and time > '" + then.isoformat() + "' " + \
        "order by track, time ;"  # limit 5

    mylg.write(q)
    rows = db.get( q )
    mylg.write("rows")
    #mylg.write(str(rows))
    return rows

# =============================

# use location for arr_apt
# use acid for trackid ???

def query_recent_tracks_fdps(db, location):

    then = get_then()

    q = "select acid, lng, lat, actualspeed, altitude, trkvel_x, trkvel_y, " + \
        "actype, dep_apt, arr_apt, flt_status, fltplan_id " + \
        "from fdps " + \
        "where " + \
        "lat is not null and lng is not null and flt_status = 'ACTIVE' " + \
        "and msg_arrival is not null " +  \
        "and msg_arrival > '" + then.isoformat() + "' " + \
        "and arr_apt = '" + location + "' " + \
        "order by fltplan_id, msg_arrival ;"

    rows = db.get( q )
    return rows

# ======================================================================

# ----------------------------
# ---------------------------- stdds
# ----------------------------

# can't get 'group by' to work, so we'll simulate it here...

def yield_each_track_stdds( rows, db, location ):

    track_counter   = 0   # small for debug purposes
    cur_tracknum    = None
    cur_points_list = [ ]

    mylg.write("a:" + str(len(rows)) )

    for g in rows:
        mylg.write("for g in")
        #mylg.write(str(g))

        this_tracknum = g[0]
        mylg.write("this_tracknum=" + str(this_tracknum))

        if this_tracknum != cur_tracknum:

            mylg.write("b")
            mylg.write("len=" + str(len(cur_points_list)) )
            # only yield if there are enough track points???
            if len(cur_points_list) > 1:
            #if 1:

                # use existing tracknum data for properties
                mylg.write("c")
                fp = query_for_fplan_stdds( db, cur_tracknum, location )
                mylg.write("d")

                if fp is not None:
                    props = { "acid"     : fp[0],
                              "actype"   : fp[1],
                              "beacon"   : fp[2],
                              "dep"      : fp[3],
                              "arr"      : fp[4]  }
                else:
                    props = { "acid"     : "na",
                              "actype"   : "na",
                              "beacon"   : "na",
                              "dep"      : "na",
                              "arr"      : "na", }

                props["trknum"] = cur_tracknum
                props["alt"]    = "9999"
                props["speed"]  = "400"
                props["hdg"]    = "90"

                mylg.write("yielding")
                yield ( cur_points_list, props )

            cur_tracknum = this_tracknum
            cur_points_list = [ [ g[1], g[2] ] ]

            track_counter += 1
            if track_counter > allow_tracks:    # <<<<<< only allow this many
                yield ( cur_points_list, props )
                mylg.write("return")
                return

        mylg.write("append")
        cur_points_list.append( [ g[1], g[2] ] )

    mylg.write("return")
    return

# ----------------------------
# ---------------------------- asdex
# ----------------------------

def yield_each_track_asdex(rows, db, location):

    track_counter   = 0   # small for debug purposes
    cur_tracknum    = None
    cur_points_list = [ ]

    for g in rows:
        this_tracknum = g[0]
        #print(this_tracknum)

        if this_tracknum != cur_tracknum:

            # only yield if there are enough track points???
            if len(cur_points_list) > 1:
            #if 1:

                # warning: aircraftid, tgttype, runway are always null

                props = { "acid"     : "na",
                          "actype"   : "na",
                          "beacon"   : "na",
                          "dep"      : "na",
                          "arr"      : "na",

                          "trknum"   : cur_tracknum,
                          "alt"      : "9999",
                          "speed"    : "400",
                          "hdg"      : "90"     }

                yield ( cur_points_list, props )

            cur_tracknum = this_tracknum
            cur_points_list = [ [ g[1], g[2] ] ]

            track_counter += 1
            if track_counter > allow_tracks:    # <<<<<< only allow this many
                return

        cur_points_list.append( [ g[1], g[2] ] )

    return

# ----------------------------
# ---------------------------- fdps
# ----------------------------

def yield_each_track_fdps( rows, db, location):

    track_counter   = 0   # small for debug purposes
    cur_tracknum    = None
    cur_points_list = [ ]

    for g in rows:
        this_tracknum = g[0]
        #print(this_tracknum)

        if this_tracknum != cur_tracknum:

            # only yield if there are enough track points???
            if len(cur_points_list) > 1:
            #if 1:

                # note: just using data from LAST point

                props = { "acid"     : cur_tracknum,
                          "actype"   : g[7],

                          "beacon"   : "na",
                          "dep"      : g[8],
                          "arr"      : g[9],

                          "trknum"   : cur_tracknum,
                          "alt"      : g[4],
                          "speed"    : g[3],
                          "hdg"      : "90"     }

                yield ( cur_points_list, props )

            cur_tracknum = this_tracknum
            cur_points_list = [ [ g[1], g[2] ] ]

            track_counter += 1
            if track_counter > allow_tracks:    # <<<<<< only allow this many
                return

        cur_points_list.append( [ g[1], g[2] ] )

    return

###############################################################

swim_sources = {
    "stdds" : ( query_recent_tracks_stdds, yield_each_track_stdds ),
    "asdex" : ( query_recent_tracks_asdex, yield_each_track_asdex ),
    "fdps"  : ( query_recent_tracks_fdps,  yield_each_track_fdps  )  }

# -------------------------------------------------

def ajax( db_csr, src, location ):
    global mylg

    #mylg = MyLog( "print" )
    mylg = MyLog( "file" )
    mylg.write("------------- starting, apt=" + location )

    db = MyPostGIS( db_access )
    #db = MyPostGIS( db_csr )

    # ------------------------

    features = [ ]   # the json object to return

    # --------------------

    # call whatever query is needed for this source
    rows = swim_sources[src][0](db, location)

    if rows is None:
        mylg.write(">>>>> rows= is None")
        return None   # fail???, but what about json encoding???

    # --------------------

    # call whatever row processing is needed for this source
    for trk, props in swim_sources[src][1]( rows, db, location ):

        props["ctype"] = "last_pos"
        a_point = { "type"       : "Feature" ,
                    "properties" : props,
                    "geometry"   : {"type" : "Point",
                                    "coordinates": trk[-1]
                                   }
                  }

        a_string = { "type"      : "Feature" ,
                     "properties": { "ctype" : "track" },
                     "geometry"  : { "type"  : "LineString",
                                     "coordinates": trk
                                   }
                   }

        features.append( a_string )
        features.append( a_point )

    #db.close()

    fc = { "type": "FeatureCollection", "features": features  }

    mylg.write("returning")
    return fc

###############################################################

if __name__ == "__main__":

    # <<<<<<<<<<<<<<<<< webfaction

    sys.path.append("/usr/lib64/python2.7/site-packages")  # where psycopg2
    sys.path.append("/home/wendell/lib/python2.7")         # where pytz is
    import psycopg2

    # ilc:
    #db_access = "dbname='cssitest' user='adminw' host='localhost' " + \
    #    " port='5432' password='shelikescarrots'"
    db_access = "dbname='swim' user='postgres' host='localhost' " + \
        " port='5433' password='425thirdst'"

    print("calling connect()")
    db_conn = psycopg2.connect( db_access )
    print("calling cursor()")
    db_csr = db_conn.cursor()
    print("connected")
    #-----------

    features = ajax( db_csr, "stdds", "PCT"  )
    #features = ajax( db_csr, "asdex", "KIAD" )
    #features = ajax( db_csr, "fdps",  "KIAD" )

    print("Status: 200 OK")
    print("Content-Type: application/json")
    print("Content-Length: 9999")
    print("")
    pprint(features)
    #print(json.dumps(features))  # cherrypy now does this!

