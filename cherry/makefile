
# make sure .html file is up-to-date, then run server
run:  track.html
	C:\Users\WTurner\Python37\python.exe serve.py

# manually do this:
# /cygdrive/c/Users/wturner/Python37/python.exe serve.py

# copy over files from npm build/dist dir
copy track.html:	../node/cssi_tracker/dist/track.html
	$(MAKE) clean
	cp ../node/cssi_tracker/dist/track*          .
	cp ../node/cssi_tracker/dist/cssi_star.*.png .
	cp ../node/cssi_tracker/dist/wt-popup*       .

# build the file
../node/cssi_tracker/dist/track.html:	../node/cssi_tracker/track.html ../node/cssi_tracker/js/tracking.js
	cd ../node/cssi_tracker && make dist

clean:
	-rm tracking.*.js
	-rm tracking.*.map
	-rm track.html
	-rm imgs/cssi_star*.png
	-rm wt-popup*.css
	-rm wt-popup*.map

