csvdataBB <- read.csv(file="BusyBox/fisher_p_or.csv", head=TRUE, sep=";")
csvdataAP <- read.csv(file="http/fisher_p_or.csv", head=TRUE, sep=";")
csvdataLDAP <- read.csv(file="OpenLDAP/fisher_p_or.csv", head=TRUE, sep=";")
csvdataVPN <- read.csv(file="OpenVPN/fisher_p_or.csv", head=TRUE, sep=";")
csvdataPID <- read.csv(file="Pidgin/fisher_p_or.csv", head=TRUE, sep=";")
csvdataSQL <- read.csv(file="SQLite/fisher_p_or.csv", head=TRUE, sep=";")

par(mfrow=c(1,1))

#goodor <- subset(csvdata$fixOR, csvdata$fixP <= 0.05)
#bador <- subset(csvdata$fixOR, csvdata$fixP > 0.05)
#gesor <- csvdata$fixOR
gesorBB <- csvdataBB$fixOR
gesorAP <- csvdataAP$fixOR
gesorLDAP <- csvdataLDAP$fixOR
gesorVPN <- csvdataVPN$fixOR
gesorPID <- csvdataPID$fixOR
gesorSQL <- csvdataSQL$fixOR

#boxplot(goodor, bador, gesor, names=c("Signifikant", "nicht Signifikant", "Gesamt"), xlab="p-Values", ylab="Odds Ratios", main="SQLite")

boxplot(gesorBB, gesorAP, gesorLDAP, gesorVPN, gesorPID, gesorSQL, names=c("BusyBox", "Apache", "OpenLDAP", "OpenVPN", "Pidgin", "SQLite"),cex=1, cex.axis=1.1, cex.main=1, cex.sub=1, xlab="System", ylab="Odds Ratios", main="",outline=FALSE)


dev.copy(png,
         "or_boxplot.png",
         width=750,
         height=450)
#dev.copy(bitmap,
#         file="test.jpeg",
#         type="jpeg")
dev.off()


par(mfrow=c(1,1))

gesorBBs <- csvdataBB$sizeOR
gesorAPs <- csvdataAP$sizeOR
gesorLDAPs <- csvdataLDAP$sizeOR
gesorVPNs <- csvdataVPN$sizeOR
gesorPIDs <- csvdataPID$sizeOR
gesorSQLs <- csvdataSQL$sizeOR

boxplot(gesorBBs, gesorAPs, gesorLDAPs, gesorVPNs, gesorPIDs, gesorSQLs, names=c("BusyBox", "Apache", "OpenLDAP", "OpenVPN", "Pidgin", "SQLite"),cex=1, cex.axis=1.1, cex.main=1, cex.sub=1, xlab="System", ylab="Odds Ratios", main="",outline=FALSE)


dev.copy(png,
         "or_boxplot_Size.png",
         width=750,
         height=450)
#dev.copy(bitmap,
#         file="test.jpeg",
#         type="jpeg")
dev.off()
