# OwnerSummary


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | 
**full_name** | **str** |  | 
**mailing_address** | [**PostalAddress**](PostalAddress.md) |  | 
**created_at** | **float** |  | 

## Example

```python
from petstore_client.models.owner_summary import OwnerSummary

# TODO update the JSON string below
json = "{}"
# create an instance of OwnerSummary from a JSON string
owner_summary_instance = OwnerSummary.from_json(json)
# print the JSON string representation of the object
print(OwnerSummary.to_json())

# convert the object into a dict
owner_summary_dict = owner_summary_instance.to_dict()
# create an instance of OwnerSummary from a dict
owner_summary_from_dict = OwnerSummary.from_dict(owner_summary_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


