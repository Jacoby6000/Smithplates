# PostalAddress


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**street** | **str** |  | 
**city** | **str** |  | 
**postal_code** | **str** |  | 

## Example

```python
from petstore_client.models.postal_address import PostalAddress

# TODO update the JSON string below
json = "{}"
# create an instance of PostalAddress from a JSON string
postal_address_instance = PostalAddress.from_json(json)
# print the JSON string representation of the object
print(PostalAddress.to_json())

# convert the object into a dict
postal_address_dict = postal_address_instance.to_dict()
# create an instance of PostalAddress from a dict
postal_address_from_dict = PostalAddress.from_dict(postal_address_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


