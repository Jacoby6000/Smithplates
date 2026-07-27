# PetSubscription


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**pet_id** | **str** |  | 

## Example

```python
from petstore_client.models.pet_subscription import PetSubscription

# TODO update the JSON string below
json = "{}"
# create an instance of PetSubscription from a JSON string
pet_subscription_instance = PetSubscription.from_json(json)
# print the JSON string representation of the object
print(PetSubscription.to_json())

# convert the object into a dict
pet_subscription_dict = pet_subscription_instance.to_dict()
# create an instance of PetSubscription from a dict
pet_subscription_from_dict = PetSubscription.from_dict(pet_subscription_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


