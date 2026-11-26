// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.oshin.windowcapture;

/** Exact resources from the documented firmware. No writable external LUT directory. */
public final class CameraFilterResources {
    private CameraFilterResources() {}
    static final java.util.Map<String,String> SHA256=java.util.Map.ofEntries(
        java.util.Map.entry("blackandwhite_HC_hdr_master.bin","4f9221120bfbe0376a9c1adb54f29783d17e939c821ec5f1b496cb38f7813bc6"),
        java.util.Map.entry("blackandwhite_HC_hdr_master_0.bin","67d747fa7955c9ac8a85c77ee83fc7214e3ec73e4b935499783d24a1d580ad76"),
        java.util.Map.entry("blackandwhite_HC_hdr_master_1.bin","15799d2458b6afaf4747d34ab749430a3679d9e248eb3a092c12078073981910"),
        java.util.Map.entry("blackandwhite_HC_hdr_normal.bin","67707ae8f588c98b0ea1ccc30f6d73f1bf17afdfd6052dbd9a0b89c31529fca2"),
        java.util.Map.entry("blackandwhite_HC_hdr_normal_0.bin","67d747fa7955c9ac8a85c77ee83fc7214e3ec73e4b935499783d24a1d580ad76"),
        java.util.Map.entry("blackandwhite_HC_hdr_normal_1.bin","e3488a9db3a8eb00d67418f0a29a8e3884005403c451500207814dc40ba5baf2"),
        java.util.Map.entry("blackandwhite_HC_master.bin","d13fc3884e9e01834169a4eb580fffc819907e72e7c0605ccc555cd2191c0958"),
        java.util.Map.entry("blackandwhite_HC_normal.bin","d13fc3884e9e01834169a4eb580fffc819907e72e7c0605ccc555cd2191c0958"),
        java.util.Map.entry("blackandwhite_HC_sdr.bin","d44d67cefb28fd83a42f1208c9300dc3cc7e8b397dc6f6cc9ec3b02f247c2d2a"),
        java.util.Map.entry("blackandwhite_HC_sdr_0.bin","67d747fa7955c9ac8a85c77ee83fc7214e3ec73e4b935499783d24a1d580ad76"),
        java.util.Map.entry("blackandwhite_HC_sdr_1.bin","fabc41d7e549b3d86d2b16aff6c9a27fb875c5b71c51c75d1674e6b6335a9464"),
        java.util.Map.entry("ccd_base_cold.bin","f7899801537d9bf93480364f2511fd29d22b9af4e6ffebf3d23e069c1c377576"),
        java.util.Map.entry("ccd_base_warm.bin","04992f6730387f55993b258cae5d30ed74e09da7eea06d7dbcb4759ac5030cec"),
        java.util.Map.entry("jzk-movie.cube.rgb.bin","fa0442a5bc30b92b02fec38e6e06e312f3a99945da42bc06217679722586f587"),
        java.util.Map.entry("portra400_hdr_master_a_1.bin","52083da28ae5cdc316addf3fed747ee9654f8af7ad22d41fb7c96615c277761c"),
        java.util.Map.entry("portra400_hdr_master_d_1.bin","6ba973778dc050bd1475a7a7ea430d7c1aee478b63fd02de177a78021effd5da"),
        java.util.Map.entry("portra400_hdr_normal_a_1.bin","46064b3ec51f946a684c4c53c3ea0eb2e453513d71892ffe1a52693ae445ea82"),
        java.util.Map.entry("portra400_hdr_normal_d_1.bin","3bf63227f7182c7729dc71902a6115085be6946e186b411e9b05285bd09a0d74"),
        java.util.Map.entry("portra400_master.bin","e0b33928857d9fa5815ad6ffe4e30d3d449095d068bf094f1bda84b93af77719"),
        java.util.Map.entry("portra400_normal.bin","0d488f82ff3bc623cc644648f113f11941c8e1dd09e6a344ee754cf6cd52dd94"),
        java.util.Map.entry("portra400_sdr_gen_a_1.bin","314acfb3d78fd92f76566585fcc2ba34b12593bc32502f494e9e1b4ce74a3d06"),
        java.util.Map.entry("portra400_sdr_gen_d_1.bin","112057b93cdc686ab02d82de38319eeef26fb7f810db896710bb80c760d9a128"),
        java.util.Map.entry("positive_hdr_master_a_1.bin","48e0d5696db7777a1703a74b437d3509e24517681bfe589c95168689489ad767"),
        java.util.Map.entry("positive_hdr_master_d_1.bin","6bbdbadb6349be87662d7f428e3c2142284520cd62ae4efe491eb969d01b61eb"),
        java.util.Map.entry("positive_hdr_normal_a_1.bin","72d47454edc6963b5e034330d9c44d3be31240d530be04b25b4b7021b6694d6b"),
        java.util.Map.entry("positive_hdr_normal_d_1.bin","43c12bd3555394eb2c21c647662a558f50ed4b4fbaeadc79033175de9249d523"),
        java.util.Map.entry("positive_master.bin","5536277c9da520318c018d72aa988746e8bc4a7d989ead8a6b462897168ab759"),
        java.util.Map.entry("positive_normal.bin","5536277c9da520318c018d72aa988746e8bc4a7d989ead8a6b462897168ab759"),
        java.util.Map.entry("positive_sdr_gen_a_1.bin","3ec700733c1e9f500b8edd1bdb0934460130bc9919bda18d8ced81951ab9b3d0"),
        java.util.Map.entry("positive_sdr_gen_d_1.bin","5e46c0e9a00289dea179fda17a9f0857a6e39bd7df5dc8eac81be6f5fd9d2281"));
    public static boolean matches(String name,byte[] bytes)throws java.security.NoSuchAlgorithmException{
        String expected=SHA256.get(name);if(expected==null||bytes.length==0||bytes.length>2_000_000)return false;
        return hasSha256(bytes, expected);
    }

    public static boolean hasSha256(byte[] bytes, String expected) throws java.security.NoSuchAlgorithmException {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex=new StringBuilder(64);for(byte b:digest){hex.append(Character.forDigit((b>>>4)&15,16));hex.append(Character.forDigit(b&15,16));}
        return expected.contentEquals(hex);
    }
}
