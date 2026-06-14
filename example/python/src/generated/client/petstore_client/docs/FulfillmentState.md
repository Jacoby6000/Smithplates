# FulfillmentState


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**pending** | **str** |  | 
**shipped** | **float** |  | 
**delivered** | **float** |  | 

## Example

```python
from petstore_client.models.fulfillment_state import FulfillmentState

# TODO update the JSON string below
json = "{}"
# create an instance of FulfillmentState from a JSON string
fulfillment_state_instance = FulfillmentState.from_json(json)
# print the JSON string representation of the object
print(FulfillmentState.to_json())

# convert the object into a dict
fulfillment_state_dict = fulfillment_state_instance.to_dict()
# create an instance of FulfillmentState from a dict
fulfillment_state_from_dict = FulfillmentState.from_dict(fulfillment_state_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


