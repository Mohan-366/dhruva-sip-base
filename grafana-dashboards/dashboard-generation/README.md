Steps to follow for generating new dashboards:

1. cd into this folder containing the ServerGroupDown.jsonnet file.
2. jb init
3. jb install https://github.com/grafana/grafonnet-lib/grafonnet
4. Update the ServerGroupDown.jsonnet file with correct server group instance name and environment
5. Format the code : jsonnetfmt -i ServerGroupDown.jsonnet
6. Test the formatted file : jsonnetfmt --test ServerGroupDown.jsonnet || echo "ERROR: File must be reformatted" >&2
7. lint the code : jsonnet-lint -J vendor/ ServerGroupDown.jsonnet
8. Generated the dashboard : jsonnet  -J vendor/ ServerGroupDown.jsonnet >> <Generated.json>
9. import the generated json in grafana.