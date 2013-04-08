from haystack import indexes
from models import SourceTextSentence

class SourceTextIndex(indexes.SearchIndex, indexes.Indexable):
    text = indexes.CharField(model_attr='sentence', document=True)
    volume = indexes.CharField(model_attr='volume__volume')
    author = indexes.CharField(model_attr='volume__source__author', faceted=True)
    author_auto = indexes.EdgeNgramField(model_attr='volume__source__author')
    title = indexes.CharField(model_attr='volume__source__title')
    online_source_name = indexes.CharField(model_attr='volume__source__online_source_name')
    online_source_url = indexes.CharField(model_attr='volume__source__online_source_link')
    print_source_name = indexes.CharField(model_attr='volume__source__print_source_name')
    print_source_url = indexes.CharField(model_attr='volume__source__print_source_link')
    enabled = indexes.BooleanField(model_attr='volume__source__enabled')

    def get_model(self):
        return SourceTextSentence
