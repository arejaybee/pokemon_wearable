def readFile():
    file = open("asdf.txt","r")
    p = []
    for line in file:
        if(not "-1" in line):
           x = line.strip()[0:5]+","
           print(x)

def removeEvos():
    file = open("asdf.txt","r")
    p = []
    evos = []
    for line in file:
        line = line.strip()
        p.append(line[1:4])
        rev = line[::-1][1:]
        evoStart = rev.find(",")
        evoToEnd = line[(-1*evoStart):]
        if("\"" in evoToEnd):
            evoName = evoToEnd[0:evoToEnd.find("\"")]
            evos.append(evoName)
    file.close()
    for poke in p:
        if(poke not in evos):
            poke = "\""+poke+"\","
            print(poke)

def allOneLIne():
    file = open("asdf.txt","r")
    p = ""
    for line in file:
        p+=line.strip().replace("\n","").replace("\r","")
    print(p)
    
allOneLIne()
