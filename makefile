
#----------------- names and variables

tarfile:=asdi_cherry_$(shell date +%d-%b-%Y_%H%M%S).tgz

#----------------- start/stop manage

manage:
	/cygdrive/c/Users/wturner/Python37/python.exe  manage.py


#----------------- cherry py server (on opanalysis3)

serve:
run:
	/cygdrive/c/Users/wturner/Python37/python.exe  server.py

#----------------- tar locally

tar:
	tar zcvf ~/backs/$(tarfile)       \
		--exclude="*/.cache/*"         \
		--exclude="*/node_modules/*"   \
		--exclude="*/dist/*"           \
		--exclude="*/logs/*"           \
		--exclude="*/bin/data/*"       \
		--exclude="*.swp"              \
		--exclude="*.log"              \
		--exclude="*.jar"              \
		--exclude="*/__pycache__"      \
		./makefile                     \
		./node                         \
		./cherry                       \
		./endpoint
	echo "filename is $(tarfile)"
	# and for a link so scp can fetch them
	cd ~/backs && ln -sf $(tarfile) latest_cherry.tgz

#----------------- tar and send to carrots

send carrots: tar
	scp ~/backs/$(tarfile) wendell@ilikecarrots.com:backups/

