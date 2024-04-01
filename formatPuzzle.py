i = 1
j = 1
with open("unsolvable.txt", 'r') as file: 
    print("{", end="")
    while True:
        char = file.read(1)
        if not char:
            print("}")
            break #EOF
        if char == '\n' and i != 9:
            print("},")
            print("{", end="")
            i = i+1
        else:
            if(j != 9):
                j = j + 1
                if(char != "."):
                    print(char, ",", sep="", end="")
                else:
                    print("0", ",", sep="", end="")
            else:
                if(char != "."):
                    print(char, end="")
                    j = 1
                else:
                    print("0", end="")
                    j = 1
