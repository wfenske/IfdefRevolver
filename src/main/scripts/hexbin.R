## See http://www.jdatalab.com/data_science_and_data_mining/2017/01/26/overplotting-r.html

##library(hexbin)

##hexbinplot(d3$num_of_orders~d3$sales_total, trans=sqrt, 
inv=function(x) x^2, type=c('g','r'), xlab="Sales Total", ylab="Number of Orders")

library(ggplot2)

p <- ggplot(data=df, aes(x=FL,y=COMMITS))  
p <- p + labs(y="COMMITS", x="FL")
p <- p + theme(axis.text=element_text(size=9), 
               axis.title = element_text(size=9, face="bold"),
               plot.title = element_text(size=9, face="bold")
               )
p <- p + theme(panel.background = element_rect(fill="white",colour ="lightblue",size=0.5,linetype="solid"),
               panel.grid.major = element_line(size=0.5,linetype='solid',colour="white"), 
               panel.grid.minor = element_line(size=0.25,linetype='solid',colour="white")
              )

### x
##sales.total.q3 <- quantile(d3$sales_total,0.75) # Q3 of sales_total

### y
##num.orders.q3 <- quantile(d3$num_of_orders, 0.75) # Q3 of num_of_orders

p.hexbin <- p + geom_hex(aes(fill="#000000",alpha=log(..count..)), fill="#0000ff") +
    scale_alpha_continuous("Log of Count", breaks=seq(0,10,1)) ##+
    ##geom_hline(yintercept = num.orders.q3, size = 0.5,color="red",linetype = 2) +
    ##geom_vline(xintercept = sales.total.q3, size = 0.5,color="red",linetype = 2)
                
p.hexbin
