csvdataBB <- read.csv(file="BusyBox/overview.csv", head=TRUE, sep=";")
csvdataAP <- read.csv(file="http/overview.csv", head=TRUE, sep=";")
csvdataLDAP <- read.csv(file="OpenLDAP/overview.csv", head=TRUE, sep=";")
csvdataVPN <- read.csv(file="OpenVPN/overview.csv", head=TRUE, sep=";")
csvdataPID <- read.csv(file="Pidgin/overview.csv", head=TRUE, sep=";")
csvdataSQL <- read.csv(file="SQLite/overview.csv", head=TRUE, sep=";")

BBab <- csvdataBB$sABC/(csvdataBB$smellAmount + csvdataBB$nSmellAmount)
BBaf <- csvdataBB$sAFC/(csvdataBB$smellAmount + csvdataBB$nSmellAmount)
BBlf <- csvdataBB$sLFC/(csvdataBB$smellAmount + csvdataBB$nSmellAmount)
BBges <- csvdataBB$smellAmount/(csvdataBB$smellAmount + csvdataBB$nSmellAmount)


APab <- csvdataAP$sABC/(csvdataAP$smellAmount + csvdataAP$nSmellAmount)
APaf <- csvdataAP$sAFC/(csvdataAP$smellAmount + csvdataAP$nSmellAmount)
APlf <- csvdataAP$sLFC/(csvdataAP$smellAmount + csvdataAP$nSmellAmount)
APges <- csvdataAP$smellAmount/(csvdataAP$smellAmount + csvdataAP$nSmellAmount)


LDAPab <- csvdataLDAP$sABC/(csvdataLDAP$smellAmount + csvdataLDAP$nSmellAmount)
LDAPaf <- csvdataLDAP$sAFC/(csvdataLDAP$smellAmount + csvdataLDAP$nSmellAmount)
LDAPlf <- csvdataLDAP$sLFC/(csvdataLDAP$smellAmount + csvdataLDAP$nSmellAmount)
LDAPges <- csvdataLDAP$smellAmount/(csvdataLDAP$smellAmount + csvdataLDAP$nSmellAmount)


VPNab <- csvdataVPN$sABC/(csvdataVPN$smellAmount + csvdataVPN$nSmellAmount)
VPNaf <- csvdataVPN$sAFC/(csvdataVPN$smellAmount + csvdataVPN$nSmellAmount)
VPNlf <- csvdataVPN$sLFC/(csvdataVPN$smellAmount + csvdataVPN$nSmellAmount)
VPNges <- csvdataVPN$smellAmount/(csvdataVPN$smellAmount + csvdataVPN$nSmellAmount)


PIDab <- csvdataPID$sABC/(csvdataPID$smellAmount + csvdataPID$nSmellAmount)
PIDaf <- csvdataPID$sAFC/(csvdataPID$smellAmount + csvdataPID$nSmellAmount)
PIDlf <- csvdataPID$sLFC/(csvdataPID$smellAmount + csvdataPID$nSmellAmount)
PIDges <- csvdataPID$smellAmount/(csvdataPID$smellAmount + csvdataPID$nSmellAmount)


SQLab <- csvdataSQL$sABC/(csvdataSQL$smellAmount + csvdataSQL$nSmellAmount)
SQLaf <- csvdataSQL$sAFC/(csvdataSQL$smellAmount + csvdataSQL$nSmellAmount)
SQLlf <- csvdataSQL$sLFC/(csvdataSQL$smellAmount + csvdataSQL$nSmellAmount)
SQLges <- csvdataSQL$smellAmount/(csvdataSQL$smellAmount + csvdataSQL$nSmellAmount)


par(mfrow=c(2,3))

boxplot(BBab, BBaf, BBlf,BBges, outline=FALSE, names=c("AB", "AF", "LF", "Ges"), ylab="Smell Anteil", main="BusyBox"
        ,cex.lab=2, cex.axis=2, cex.main=3, cex.sub=2)
boxplot(APab, APaf, APlf,APges,outline=FALSE, names=c("AB", "AF", "LF", "Ges"), ylab="Smell Anteil", main="Apache"
        ,cex.lab=2, cex.axis=2, cex.main=3, cex.sub=2)
boxplot(LDAPab, LDAPaf, LDAPlf,LDAPges,outline=FALSE, names=c("AB", "AF", "LF", "Ges"), ylab="Smell Anteil", main="OpenLDAP"
        ,cex.lab=2, cex.axis=2, cex.main=3, cex.sub=2)
boxplot(VPNab, VPNaf, VPNlf,VPNges,outline=FALSE, names=c("AB", "AF", "LF", "Ges"), ylab="Smell Anteil", main="OpenVPN"
        ,cex.lab=2, cex.axis=2, cex.main=3, cex.sub=2)
boxplot(PIDab, PIDaf, PIDlf,PIDges,outline=FALSE, names=c("AB", "AF", "LF", "Ges"), ylab="Smell Anteil", main="Pidgin"
        ,cex.lab=2, cex.axis=2, cex.main=3, cex.sub=2)
boxplot(SQLab, SQLaf, SQLlf,SQLges,outline=FALSE, names=c("AB", "AF", "LF", "Ges"), ylab="Smell Anteil", main="SQLite"
        ,cex.lab=2, cex.axis=2, cex.main=3, cex.sub=2)

dev.copy(png,
         "smellBoxplots.png",
         width=1600,
         height=1000)
#dev.copy(bitmap,
#         file="test.jpeg",
#         type="jpeg")
dev.off()

