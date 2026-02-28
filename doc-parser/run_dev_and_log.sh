sh build_image.sh 20251030
sh run_docker_cpu.sh 20251030 7100
docker logs -f general-doc-parser-dev
