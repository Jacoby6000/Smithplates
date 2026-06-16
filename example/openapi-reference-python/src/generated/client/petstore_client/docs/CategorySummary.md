# CategorySummary


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | 
**name** | **str** |  | 
**store_id** | **str** |  | 

## Example

```python
from petstore_client.models.category_summary import CategorySummary

# TODO update the JSON string below
json = "{}"
# create an instance of CategorySummary from a JSON string
category_summary_instance = CategorySummary.from_json(json)
# print the JSON string representation of the object
print(CategorySummary.to_json())

# convert the object into a dict
category_summary_dict = category_summary_instance.to_dict()
# create an instance of CategorySummary from a dict
category_summary_from_dict = CategorySummary.from_dict(category_summary_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


