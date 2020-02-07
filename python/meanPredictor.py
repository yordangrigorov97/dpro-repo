import numpy as np
import pandas as pd


def trainer(data):
    classifier = data.drop("actor", axis=1).groupby(
        ["emotion"]).mean().sort_values(by=['a'])

    num_emotions = classifier.shape[0]
    minAlpha = [None] * num_emotions
    maxAlpha = [None] * num_emotions
    minAlpha[0] = float("-inf")
    maxAlpha[num_emotions-1] = float("inf")

    for i in range(1, num_emotions):
        # "a" is 0th column, because "emotion" is index.
        minAlpha[i] = (classifier.iloc[i-1, 0]+classifier.iloc[i, 0])/2

    for i in range(0, num_emotions-1):
        maxAlpha[i] = (classifier.iloc[i+1, 0]+classifier.iloc[i, 0])/2

    classifier["minAlpha"] = minAlpha
    classifier["maxAlpha"] = maxAlpha

    return classifier


def classifier(data, classifier):
    def classify(elem):
        for emo_index in range(classifier.shape[0]):
            if elem < classifier.iloc[emo_index,1]: #if we've outreached the min Alpha value
                return classifier.index[emo_index-1] #then the answer is the previous emotion
        return classifier.index[-1] #for Wut to workFF

    sentences = data.groupby(["actor","sentence","emotion","try"], as_index=False).mean()
    sentences["predicted_emo"]=sentences["a"].apply(classify)


    sentences["match_emo"]=sentences["emotion"]==sentences["predicted_emo"]
    return sentences
