DESCRIPTION = "The .NET Core runtime and SDK"
HOMEPAGE = "http://dot.net/"
LICENSE = "MIT"
SECTION = "devel"

LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=42b611e7375c06a28601953626ab16cb"

def get_core_runtime_id(d):
    targetarch = d.getVar('TARGET_ARCH').lower()
    map = {
        "aarch64" : "linux-arm64",
        "arm": "linux-arm",
        "x86_64": "linux-x64"
    }
    runtimeid = map.get(targetarch)
    if runtimeid is None:
        bb.fatal("Unsupported target architecture. You can try building dotnet core from sources instead.")
    return runtimeid

CORE_RUNTIME_ID = "${@get_core_runtime_id(d)}"

SRC_URI = "https://dotnetcli.blob.core.windows.net/dotnet/Sdk/${PV}/dotnet-sdk-${PV}-${CORE_RUNTIME_ID}.tar.gz"
SRC_URI[md5sum] = "20dd17043d6f500eb8369c4a26f3a602"
SRC_URI[sha256sum] = "b65034be854a5cfcc2afe1a06fc6ed25a6d05406bc00670225fa56982efe9f2d"

SRC_URI_append_class-native = " file://dotnet-native"

DEPENDS += "patchelf-native jq-native"
RDEPENDS_${PN} = "libicuuc libicui18n libcurl libssl krb5"
RDEPENDS_${PN}_class-native = "icu-native curl-native openssl-native"

S = "${WORKDIR}/dotnet-sdk-${PV}-${CORE_RUNTIME_ID}"

python base_do_unpack() {
    src_uri = (d.getVar('SRC_URI') or "").split()
    if len(src_uri) == 0:
        return
    try:
        fetcher = bb.fetch2.Fetch(src_uri, d)
        fetcher.unpack(d.getVar('S'))
    except bb.fetch2.BBFetchException as e:
        bb.fatal(str(e))
}

INSANE_SKIP_${PN} += "already-stripped file-rdeps staticdev libdir"
INSANE_SKIP_${PN}-dbg += "libdir"
INSANE_SKIP_${PN}-dev += "already-stripped file-rdeps staticdev libdir"
SKIP_FILEDEPS_${PN} = '1'
SKIP_FILEDEPS_${PN}-dev = '1'

do_install() {
    # Change the interpreter for all the executables.
    # The onces in the tarball are "/lib64/ld-linux-x86-64.so.2", we need "/lib/ld-linux-x86-64.so.2"
    find -type f -executable -exec sh -c "file -i '{}' | grep -q 'x-executable; charset=binary'" \; -print | xargs -L1 patchelf --set-interpreter /lib/ld-linux-armhf.so.3

    # Remove libcoreclrtraceptprovider.so
    rm -f ${S}/shared/Microsoft.NETCore.App/2.2.2/libcoreclrtraceptprovider.so
    # Remove the reference to libcoreclrtraceptprovider.so in the deps.json file.
    rm -f ${WORKDIR}/tmp.json
    cat ${S}/shared/Microsoft.NETCore.App/2.2.2/Microsoft.NETCore.App.deps.json | \
      jq 'del(."targets".".NETCoreApp,Version=v2.2/${CORE_RUNTIME_ID}"."runtime.${CORE_RUNTIME_ID}.Microsoft.NETCore.App/2.2.2"."native"."runtimes/${CORE_RUNTIME_ID}/native/libcoreclrtraceptprovider.so")' \
      > ${WORKDIR}/tmp.json
    cp ${WORKDIR}/tmp.json ${S}/shared/Microsoft.NETCore.App/2.2.2/Microsoft.NETCore.App.deps.json

	install -d ${D}${datadir}/dotnet
	cp -rp ${S}/* ${D}${datadir}/dotnet

    install -d ${D}${bindir}
    ln -s ../..${datadir}/dotnet/dotnet ${D}${bindir}/dotnet

    # The installers delivered from Microsoft have uid of 1000.
    chown -R root:root ${D}
}

do_install_append_class-native() {
    # Use our custom dotnet script that unloads psuedo
    rm ${D}${bindir}/dotnet
    cp ${S}/dotnet-native ${D}${bindir}/dotnet
    rm ${D}${datadir}/dotnet/dotnet-native
}

FILES_${PN}-dev += "/usr/share/dotnet/sdk"

BBCLASSEXTEND = "native"