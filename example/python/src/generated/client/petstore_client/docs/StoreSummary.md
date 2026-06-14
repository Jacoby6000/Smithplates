# StoreSummary


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | 
**name** | **str** |  | 

## Example

```python
from petstore_client.models.store_summary import StoreSummary

# TODO update the JSON string below
json = "{}"
# create an instance of StoreSummary from a JSON string
store_summary_instance = StoreSummary.from_json(json)
# print the JSON string representation of the object
print(StoreSummary.to_json())

# convert the object into a dict
store_summary_dict = store_summary_instance.to_dict()
# create an instance of StoreSummary from a dict
store_summary_from_dict = StoreSummary.from_dict(store_summary_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


