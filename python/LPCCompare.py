import numpy as np
import pandas as pd

import meanPredictor

# sample from file:
# 03,a01,F,a,1575414000188,0.08776753342782448
# 03,a01,F,a,1575414000188,0.07242514178149331
def print_full(x):
    pd.set_option('display.max_rows', len(x))
    print(x)
    pd.reset_option('display.max_rows')


def main():
    data = pd.read_csv("LPCMultipleout.csv", delimiter=',',usecols = (0,1,2,3,5)
                    ,names=["actor","sentence","emotion","try","a"]) #max_rows=90000

    data = data[data.index % 20 == 0] #todo check if correct

    
    trainData=data.sample(frac=0.8,random_state=1) #WARNING sentences are no longer in order
    print(trainData,"\n\n")
    testData=data.drop(trainData.index)
    print(testData)
    
    classifier=meanPredictor.trainer(trainData)
    #example classifier
    #                 a  minAlpha  maxAlpha
    # emotion
    # T        0.074506      -inf  0.104964
    # L        0.135422  0.104964  0.137152
    # N        0.138883  0.137152  0.202393
    # A        0.265904  0.202393  0.270122
    # E        0.274341  0.270122  0.330193
    # F        0.386044  0.330193  0.419032
    # W        0.452020  0.419032       inf

    sentences = meanPredictor.classifier(testData,classifier)
    #print_full(sentences)

    correctness=sentences["match_emo"].sum()/sentences.shape[0]
    print("we are "+str(correctness)+" correct")
    comparison = correctness-(1/classifier.shape[0])
    print("thats "+str(comparison)+" better than random")





if __name__ == "__main__":
    main()

