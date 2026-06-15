# PlaceOrderResponseContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | 

## Example

```python
from petstore_client.models.place_order_response_content import PlaceOrderResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of PlaceOrderResponseContent from a JSON string
place_order_response_content_instance = PlaceOrderResponseContent.from_json(json)
# print the JSON string representation of the object
print(PlaceOrderResponseContent.to_json())

# convert the object into a dict
place_order_response_content_dict = place_order_response_content_instance.to_dict()
# create an instance of PlaceOrderResponseContent from a dict
place_order_response_content_from_dict = PlaceOrderResponseContent.from_dict(place_order_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


