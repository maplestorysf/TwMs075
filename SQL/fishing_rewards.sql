SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for `fishing_rewards`
-- ----------------------------
DROP TABLE IF EXISTS `fishing_rewards`;
CREATE TABLE `fishing_rewards` (
  `itemid` int(11) NOT NULL,
  `chance` int(11) NOT NULL,
  `expiration` int(11) DEFAULT '0',
  `name` varchar(255) DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of fishing_rewards
-- ----------------------------
INSERT INTO `fishing_rewards` VALUES ('2000000', '1000', '0', '紅色藥水');
INSERT INTO `fishing_rewards` VALUES ('2000001', '1000', '0', '橘色藥水');
INSERT INTO `fishing_rewards` VALUES ('2000002', '1000', '0', '白色藥水');
INSERT INTO `fishing_rewards` VALUES ('2000003', '1000', '0', '藍色藥水');
INSERT INTO `fishing_rewards` VALUES ('2000004', '5000', '0', '特殊藥水');
INSERT INTO `fishing_rewards` VALUES ('2000005', '3000', '0', '超級藥水');
INSERT INTO `fishing_rewards` VALUES ('2000006', '1000', '0', '活力藥水');
INSERT INTO `fishing_rewards` VALUES ('4031627', '50400', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031628', '50400', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031630', '50400', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031631', '50400', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031633', '43680', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031634', '43680', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031635', '40320', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031636', '36960', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031637', '43680', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031638', '43680', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031639', '40320', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031640', '36960', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031641', '43680', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031642', '43680', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031643', '40320', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031644', '36960', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031645', '43680', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031646', '43680', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031647', '40320', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031648', '36960', '0', '');
INSERT INTO `fishing_rewards` VALUES ('4031922', '5000', '0', '便便');
INSERT INTO `fishing_rewards` VALUES ('1002600', '100', '0', '紅色楓葉頭帶');
INSERT INTO `fishing_rewards` VALUES ('1002601', '100', '0', '黃色楓葉頭帶');
INSERT INTO `fishing_rewards` VALUES ('1002602', '100', '0', '藍色楓葉頭帶');
INSERT INTO `fishing_rewards` VALUES ('1002600', '200', '14', '紅色楓葉頭帶');
INSERT INTO `fishing_rewards` VALUES ('1002601', '200', '14', '黃色楓葉頭帶');
INSERT INTO `fishing_rewards` VALUES ('1002602', '200', '14', '藍色楓葉頭帶');
INSERT INTO `fishing_rewards` VALUES ('1002603', '200', '14', '白色楓葉頭帶');
INSERT INTO `fishing_rewards` VALUES ('1002603', '100', '0', '白色楓葉頭帶');
INSERT INTO `fishing_rewards` VALUES ('1102040', '50', '0', '黃色冒險家披風');
INSERT INTO `fishing_rewards` VALUES ('1102041', '50', '0', '粉紅冒險家披風');
INSERT INTO `fishing_rewards` VALUES ('1102042', '50', '0', '紫色冒險家披風');
INSERT INTO `fishing_rewards` VALUES ('1102043', '600', '0', '褐色冒險家披風');
INSERT INTO `fishing_rewards` VALUES ('1102000', '600', '0', '綠色冒險披風');
INSERT INTO `fishing_rewards` VALUES ('1102001', '600', '0', '藍色冒險披風');
INSERT INTO `fishing_rewards` VALUES ('1102002', '600', '0', '紅色冒險披風');
INSERT INTO `fishing_rewards` VALUES ('1102003', '600', '0', '白色冒險披風');
INSERT INTO `fishing_rewards` VALUES ('1102004', '600', '0', '黑色冒險披風');
INSERT INTO `fishing_rewards` VALUES ('1102026', '600', '0', '綠妖精披風');
INSERT INTO `fishing_rewards` VALUES ('1102027', '600', '0', '藍妖精披風');
INSERT INTO `fishing_rewards` VALUES ('1102026', '600', '0', '紅妖精披風');
INSERT INTO `fishing_rewards` VALUES ('1102029', '600', '0', '白妖精披風');
INSERT INTO `fishing_rewards` VALUES ('1102030', '600', '0', '黑妖精披風');
INSERT INTO `fishing_rewards` VALUES ('1102060', '3', '14', '粉紅蝴蝶結');
INSERT INTO `fishing_rewards` VALUES ('4031302', '8000', '0', '海底垃圾');
