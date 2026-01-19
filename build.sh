#!/bin/sh

_curDir=`pwd`
_build_script=$(basename "$0")
_repoTopDir=$(cd "$(dirname "$0")";pwd)
_num_of_opts="$#"
_cmd_opt_str="$@"

RC_JAVA_PLUGINS="aws azure cyclecloud google"
RC_SCRIPT_PLUGINS="awsv2 ibmcloudgen2 openstack"
ALL_RC_PLUGSIN="$RC_JAVA_PLUGINS $RC_SCRIPT_PLUGINS"
#RC_TARGET_FILE="brel_root.rc_plugin.tar.gz"
RC_TARGET_FILE="brel_root.hf_providers_java_plugin.tar.gz"

__JAVA_TOOL="mvn"
__DO_BUILD=""
__DO_CLEAN=""


#--------------------------------------------------------
# Name: build_rc_usage
# Synopsis: build_rc_usage
# Description:
#       Usage of build.sh
#--------------------------------------------------------
build_rc_usage ()
{

cat << BUILD_RC_USAGE

Usage:  ${_build_script} [-b|--build] [--tool=mvn|ant] 
        ${_build_script} -c|--clean [--tool=mvn|ant] 
        ${_build_script} -h|--help

-b|--build: [Default] Build all Resource Connector plugins. 
            Create ${RC_TARGET_FILE}
            You are required to specify JAVA_HOME and MAVEN_HOME(or ANT_HOME),
            and add java and mvn(or ant) into your PATH env.
-c|--clean: Clean generated java class and jar files.
--tool:     Specify the tool to compile java plugins, maven(default) or ant
-h:         Print command usage and exit

BUILD_RC_USAGE
} # build_rc_usage


#--------------------------------------------------------
# Name: do_clean
# Synopsis: do_clean
# Description:
#       Do cleanup of complied java class and jar files
#--------------------------------------------------------
do_clean()
{
    cd $_repoTopDir
    cd hostProviders
    # clean java class and jar files
    for PDIR in $RC_JAVA_PLUGINS; do
        cd $PDIR
        ${__JAVA_TOOL} clean
        rm -rf lib
        cd -
    done

    cd $_repoTopDir
    rm -rf resource_connector
    rm -f $RC_TARGET_FILE
    cd $_curDir
} # do_clean


#--------------------------------------------------------
# Name: build_rc_usage
# Synopsis: build_rc_usage
# Description:
#       This function displays the usage of build.sh
#--------------------------------------------------------
build_rc () 
{
    cd $_repoTopDir
    cd hostProviders
    # build jar files
    for PDIR in $RC_JAVA_PLUGINS; do
        cd $PDIR
        if [[ "$__JAVA_TOOL" = "ant" ]]; then
            ant clean
            ant jar
        else
            mvn clean
            mvn package
            rm -rf lib
            mv target/lib .
        fi
        cd -
    done
    
    cd $_repoTopDir
    mkdir $_repoTopDir/resource_connector
    # copy files 
    for PDIR in $ALL_RC_PLUGSIN; do
        mkdir $_repoTopDir/resource_connector/$PDIR

        # copy lib 
        if [[ -d $_repoTopDir/hostProviders/$PDIR/lib ]]; then
            cp -rf $_repoTopDir/hostProviders/$PDIR/lib $_repoTopDir/resource_connector/$PDIR/lib
        fi

        # copy conf
        cp -rf $_repoTopDir/hostProviders/$PDIR/conf/lsf $_repoTopDir/resource_connector/$PDIR/conf

        # copy auentication file
        _cred_file=$_repoTopDir/hostProviders/$PDIR/conf/credentials
        if [[ -f $_cred_file ]]; then
            cp $_cred_file $_repoTopDir/resource_connector/$PDIR/conf
        fi

        # copy scripts
        cp -rf $_repoTopDir/hostProviders/$PDIR/scripts $_repoTopDir/resource_connector/$PDIR/scripts

        # copy example_user_data.sh
        _user_data_dir=$_repoTopDir/hostProviders/$PDIR/postprovision/lsf
        if [[ -d $_user_data_dir ]]; then
            cp $_user_data_dir/*.sh $_repoTopDir/resource_connector/$PDIR/scripts
        fi

        if [[ -d $_repoTopDir/resource_connector/$PDIR/lib/ ]]; then
            chmod 755 $_repoTopDir/resource_connector/$PDIR/lib/* 
        fi
        chmod 755 $_repoTopDir/resource_connector/$PDIR/scripts/*
        chmod 644 $_repoTopDir/resource_connector/$PDIR/conf/*
    done
    
    cd $_repoTopDir

    # copy specificial files
    cp -rf $_repoTopDir/hostProviders/example_hostProviders.json $_repoTopDir/resource_connector/
    cp -rf $_repoTopDir/policy $_repoTopDir/resource_connector/
    mv $_repoTopDir/resource_connector/policy/example_policy_config.json $_repoTopDir/resource_connector
    chmod 755 $_repoTopDir/resource_connector/policy/*.py
    
    tar czvf $RC_TARGET_FILE resource_connector/ 
    rm -rf resource_connector
    cd $_curDir
} # build_rc()
    

################################################################################
################################################################################
#
# Main procedure begin from here
#
if [[ "$_num_of_opts" = "0" ]]; then
    build_rc
    exit 0
fi

for _opt in $_cmd_opt_str
do
    case $_opt in
        --tool=*)
            __JAVA_TOOL=${_opt#*=}
            if [[ "$__JAVA_TOOL" = "mvn" ]]; then
                __JAVA_TOOL="mvn"
            elif [[ "$__JAVA_TOOL" = "ant" ]]; then
                __JAVA_TOOL="ant"
            else 
                echo "The tool $__JAVA_TOOL is not supported. Use mvn as default."
                __JAVA_TOOL="mvn"
            fi
            ;;
        --build|-b)
            __DO_BUILD="Y"
            ;;
        --clean|-c)
            __DO_CLEAN="Y"
            ;;
        --help|-h)
            build_rc_usage
            exit 0
            ;;
        *)
            echo "    Command line option $_opt is not valid."
            build_rc_usage
            exit 1
            ;;
    esac
done

# nothing specified but --tool, default do build
if [[ -z $__DO_CLEAN && -z $__DO_BUILD ]]; then
    __DO_BUILD="Y"
fi


# Do cleanup
if [[ "$__DO_CLEAN" = "Y" ]]; then
    do_clean
fi

# Do build
if [[ "$__DO_BUILD" = "Y" ]]; then
    build_rc
fi

exit 0

